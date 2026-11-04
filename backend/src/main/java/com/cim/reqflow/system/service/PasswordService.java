package com.cim.reqflow.system.service;

import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysUserMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 口令相关逻辑的唯一样口：登录校验、自助改密、管理员重置共用同一套规则，
 * 避免「三处各写一遍 bcrypt，改一处忘两处」。
 * <p>
 * 哈希统一按 $2a$ 生成（jBCrypt 只认这个前缀）；种子数据里的 $2b$ 与 $2a$ 算法一致，
 * 比对前把前缀归一即可，不重新哈希存量数据。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class PasswordService {

    /** bcrypt 代价因子。10 约 100ms，兼顾安全与登录响应 */
    private static final int BCRYPT_LOG_ROUNDS = 10;

    /** jBCrypt 支持的哈希前缀 */
    private static final String HASH_PREFIX_2A = "$2a$";

    /** 种子数据使用的前缀，jBCrypt 不直接支持，需归一为 2A */
    private static final String HASH_PREFIX_2B = "$2b$";

    /** 前缀长度，替换前缀时从该位置截取盐与摘要 */
    private static final int HASH_PREFIX_LENGTH = 4;

    /** 口令最小长度 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    /** 新用户初始口令与管理员重置后的口令，生产用环境变量覆盖，不把真口令写进代码 */
    private final String initialPassword;

    /** 用户表数据访问 */
    private final SysUserMapper sysUserMapper;

    public PasswordService(SysUserMapper sysUserMapper,
                           @Value("${cim.auth.default-password}") String initialPassword) {
        this.sysUserMapper = sysUserMapper;
        this.initialPassword = initialPassword;
    }

    /**
     * 取统一初始口令。新增用户与重置口令都用它，避免各处硬编码。
     *
     * @return 初始明文口令
     */
    public String getInitialPassword() {
        return initialPassword;
    }

    /**
     * 生成口令哈希，新写入的一律 $2a$ 前缀。
     *
     * @param plainPassword 明文口令
     * @return 哈希串
     */
    public String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * 比对明文口令与库内哈希。
     * 种子数据由 Python bcrypt 生成为 $2b$，与 $2a$ 算法一致，归一前缀后再比。
     *
     * @param plainPassword 明文口令
     * @param passwordHash  库内哈希
     * @return true 表示口令正确
     */
    public boolean matches(String plainPassword, String passwordHash) {
        if (plainPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        String normalizedHash = passwordHash.startsWith(HASH_PREFIX_2B)
                ? HASH_PREFIX_2A + passwordHash.substring(HASH_PREFIX_LENGTH)
                : passwordHash;
        return BCrypt.checkpw(plainPassword, normalizedHash);
    }

    /**
     * 当前登录人自助改密。
     * 改完清除 must_change_password，否则下次登录还会被要求改密。
     *
     * @param userId        用户 ID
     * @param oldPassword   原口令
     * @param newPassword   新口令
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String userId, String oldPassword, String newPassword) {
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在", BizException.STATUS_UNAUTHORIZED);
        }
        if (!matches(oldPassword, user.getPasswordHash())) {
            throw new BizException("原密码不正确");
        }
        checkPasswordStrength(newPassword);
        if (matches(newPassword, user.getPasswordHash())) {
            throw new BizException("新密码不能与原密码相同");
        }

        user.setPasswordHash(hash(newPassword));
        user.setMustChangePassword(SystemConstants.FLAG_NO);
        user.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysUserMapper.updateById(user);
    }

    /**
     * 管理员重置为初始口令，并要求该用户下次登录立即改密。
     *
     * @param userId 用户 ID
     * @return 初始明文口令，由前端一次性提示给管理员
     */
    @Transactional(rollbackFor = Exception.class)
    public String resetToInitialPassword(String userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setPasswordHash(hash(initialPassword));
        user.setMustChangePassword(SystemConstants.FLAG_YES);
        user.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysUserMapper.updateById(user);
        return initialPassword;
    }

    /**
     * 校验新口令强度：至少 8 位，且不能是纯数字（避免 12345678 这类）。
     *
     * @param newPassword 新口令
     */
    private void checkPasswordStrength(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException("新密码至少 8 位");
        }
        boolean hasNonDigit = false;
        for (char character : newPassword.toCharArray()) {
            if (!Character.isDigit(character)) {
                hasNonDigit = true;
                break;
            }
        }
        if (!hasNonDigit) {
            throw new BizException("新密码不能是纯数字");
        }
    }
}
