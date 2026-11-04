package com.cim.reqflow.ticket.storage;

import com.cim.reqflow.common.BizException;
import com.cim.reqflow.ticket.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 附件文件存储：负责落地、寻址、删除、指纹与正文提取。
 * <p>
 * 与数据库的分工：本类只管文件系统，{@code req_attachment} 的写入由服务层负责。
 * 库里只存相对路径，绝对路径由 {@link UploadProperties#getUploadDir()} 拼出来，
 * 换机器迁移附件目录时不用改一行数据。
 * <p>
 * 目录规则（见 07 §3.1）：{@code {projectCode}/{ticketNo}/{stage}/{uuid}.{ext}}。
 * 按项目分一级，导出某个项目的证据包时直接打包一个目录即可。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);

    /** 路径片段只允许字母、数字、下划线、短横线。项目编码与单号都符合，其余一律替换，杜绝路径穿越 */
    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[^A-Za-z0-9_-]");

    /** 文件名额外允许点号 —— 去掉点会让磁盘上出现「uuid_md」这种看不出类型的名字，运维排查时很难受 */
    private static final Pattern UNSAFE_FILE_NAME = Pattern.compile("[^A-Za-z0-9._-]");

    /** 扩展名只取字母数字，长度上限，防止用超长后缀绕过判断 */
    private static final Pattern SAFE_EXT = Pattern.compile("[a-z0-9]{1,10}");

    /** 正文提取的读取上限，超过部分丢弃。附件正文只喂给 AI 做摘要，不需要全文 */
    private static final int MAX_TEXT_BYTES = 256 * 1024;

    /** 提取结果的字符上限，避免 CLOB 与提示词被撑爆 */
    private static final int MAX_TEXT_CHARS = 20000;

    /** docx 的正文部件名，其余部件（样式、关系）没有文字内容 */
    private static final String DOCX_DOCUMENT_PART = "word/document.xml";

    /** 段落结束标记，必须先于去标签处理，否则所有段落会粘成一行 */
    private static final String DOCX_PARAGRAPH_END = "</w:p>";

    /** 制表符标记 */
    private static final String DOCX_TAB = "<w:tab/>";

    /** 任意 XML 标签 */
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");

    /** 附件根目录，构造时解析一次 */
    private final Path rootDir;

    /** 单文件上限（字节） */
    private final long maxSizeBytes;

    public AttachmentStorage(UploadProperties uploadProperties) {
        this.rootDir = Path.of(uploadProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxSizeBytes = uploadProperties.getUploadMaxMb() * 1024L * 1024L;
        log.info("附件根目录={}，单文件上限={}MB", rootDir, uploadProperties.getUploadMaxMb());
    }

    /**
     * 单文件大小上限（字节），供服务层做前置校验。
     *
     * @return 上限字节数
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /**
     * 取文件扩展名（小写，不含点）。没有扩展名或不合规时返回空串。
     *
     * @param originalName 原始文件名
     * @return 规范化扩展名
     */
    public String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return SAFE_EXT.matcher(ext).matches() ? ext : "";
    }

    /**
     * 拼接相对路径。片段里的非法字符统一替换成下划线。
     *
     * @param projectCode 项目编码
     * @param ticketNo    单据编号
     * @param stage       上传时所处状态码
     * @param storedName  存储文件名（UUID 加扩展名）
     * @return 相对路径，统一用正斜杠，跨平台入库一致
     */
    public String buildRelativePath(String projectCode, String ticketNo, String stage, String storedName) {
        return clean(projectCode) + "/" + clean(ticketNo) + "/" + clean(stage) + "/"
                + cleanFileName(storedName);
    }

    /**
     * 把输入流写入目标相对路径，同时计算 SHA-256。
     * <p>
     * 用流式写入而不是先读进内存：50MB 的文件读成 byte[] 会在大并发下把堆撑爆，
     * 而附件上传恰恰是最容易出现多个大文件同时在途的操作。
     *
     * @param relativePath 相对路径
     * @param inputStream  文件流，由调用方负责关闭
     * @return 文件指纹，64 位小写十六进制
     */
    public String save(String relativePath, InputStream inputStream) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestStream = new DigestInputStream(inputStream, digest)) {
                Files.copy(digestStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 JDK 必须实现的算法，走不到这里；真走到说明运行环境被裁剪过
            throw new BizException("附件指纹计算失败：当前 JRE 不支持 SHA-256", exception);
        } catch (IOException exception) {
            throw new BizException("附件写入失败，请检查上传目录是否可写：" + rootDir, exception);
        }
    }

    /**
     * 把相对路径解析成绝对路径，并确认没有跳出根目录。
     * <p>
     * 相对路径虽然全部由本类拼接，但库里存的字符串终究是外部输入，
     * 一旦有历史脏数据带着 {@code ../}，下载接口就会变成任意文件读取。
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    public Path resolve(String relativePath) {
        Path target = rootDir.resolve(relativePath).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BizException("附件路径非法：" + relativePath);
        }
        return target;
    }

    /**
     * 判断文件是否还在。库里记录与磁盘文件可能不同步（运维直接挪走过目录），
     * 下载前必须确认，否则用户拿到的是 500 而不是「文件已丢失」。
     *
     * @param relativePath 相对路径
     * @return 存在返回 true
     */
    public boolean exists(String relativePath) {
        return Files.isRegularFile(resolve(relativePath));
    }

    /**
     * 删除物理文件。文件已不存在时不报错 —— 删除接口的目的就是「让它消失」，
     * 已经消失算达成目标。
     *
     * @param relativePath 相对路径
     */
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException exception) {
            // 软删除已经写库，物理文件删不掉只会占空间，不影响业务，记日志交给运维清理
            log.warn("附件物理文件删除失败，路径={}", relativePath, exception);
        }
    }

    /**
     * 提取正文，供 AI 分析与全文检索使用。不支持的格式返回 null。
     * <p>
     * 一期覆盖纯文本与 docx。docx 本质是 zip，用 JDK 自带的 zip 解出
     * {@code word/document.xml} 再去标签即可，不必为此引入 POI：
     * 一个 10MB 的依赖只为了读一段 XML，不划算。PDF 留到二期。
     *
     * @param relativePath 相对路径
     * @param ext          扩展名（小写）
     * @return 正文，提取不到或格式不支持时返回 null
     */
    public String extractText(String relativePath, String ext) {
        if (ext == null || ext.isEmpty()) {
            return null;
        }
        Path file = resolve(relativePath);
        try {
            if ("docx".equals(ext)) {
                return truncate(extractDocx(file));
            }
            if (isPlainText(ext)) {
                return truncate(readPlainText(file));
            }
            return null;
        } catch (IOException exception) {
            // 提取失败不能影响上传本身：附件已经存好、库已落，正文只是增强信息
            log.warn("附件正文提取失败，路径={}，扩展名={}", relativePath, ext, exception);
            return null;
        }
    }

    /**
     * 纯文本类扩展名。这些格式按文本读，其余按二进制处理。
     *
     * @param ext 扩展名
     * @return 是否按文本读取
     */
    private boolean isPlainText(String ext) {
        switch (ext) {
            case "txt":
            case "md":
            case "csv":
            case "json":
            case "xml":
            case "yml":
            case "yaml":
            case "log":
            case "sql":
                return true;
            default:
                return false;
        }
    }

    /**
     * 读取纯文本前若干字节。用 UTF-8 解码，非法字节会被替换成占位符而不是抛异常。
     *
     * @param file 文件路径
     * @return 正文
     * @throws IOException 读取失败
     */
    private String readPlainText(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = inputStream.readNBytes(MAX_TEXT_BYTES);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    /**
     * 从 docx 里取出正文。
     *
     * @param file 文件路径
     * @return 正文；不是合法 docx 时返回 null
     * @throws IOException 读取失败
     */
    private String extractDocx(Path file) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                if (DOCX_DOCUMENT_PART.equals(entry.getName())) {
                    byte[] xmlBytes = zipStream.readNBytes(MAX_TEXT_BYTES);
                    return stripDocxTags(new String(xmlBytes, StandardCharsets.UTF_8));
                }
                entry = zipStream.getNextEntry();
            }
        }
        return null;
    }

    /**
     * 把 document.xml 还原成可读文本：段落转行、制表符保留、其余标签去掉、实体反转义。
     *
     * @param xml 正文部件内容
     * @return 纯文本
     */
    private String stripDocxTags(String xml) {
        String text = xml.replace(DOCX_PARAGRAPH_END, "\n")
                .replace(DOCX_TAB, "\t");
        text = XML_TAG.matcher(text).replaceAll("");
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
        // 去标签后常留下成片的空白行，压成单空行，避免 AI 输入里一半是换行
        return text.replaceAll("[\\t ]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * 截断正文到字符上限。
     *
     * @param text 正文
     * @return 截断后的正文
     */
    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }

    /**
     * 清洗路径片段。
     *
     * @param segment 原始片段
     * @return 只含安全字符的片段
     */
    private String clean(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "UNKNOWN";
        }
        return UNSAFE_SEGMENT.matcher(segment).replaceAll("_");
    }

    /**
     * 清洗存储文件名，允许点号保留扩展名。
     * <p>
     * 额外把 {@code ..} 替换掉：文件名虽然是 UUID 拼出来的，但这个方法也可能被
     * 后续新增的调用方复用，不能让「带两点的名字」有穿过目录的机会。
     *
     * @param fileName 原始文件名
     * @return 只含安全字符的文件名
     */
    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "UNKNOWN";
        }
        String cleaned = UNSAFE_FILE_NAME.matcher(fileName).replaceAll("_");
        return cleaned.replace("..", "__");
    }

    /**
     * 字节数组转十六进制小写字符串。
     *
     * @param bytes 摘要字节
     * @return 十六进制字符串
     */
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
