package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.dto.DictDataSaveDTO;
import com.cim.reqflow.system.dto.DictTypeSaveDTO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysDictTypeDO;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysDictTypeMapper;
import com.cim.reqflow.system.vo.DictItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 字典服务：三个查询接口（全量 / 单个 / 版本号）加系统管理的增删改。
 * 版本号的作用：字典改动即 +1，前端轮询比对版本号决定是否重拉全量。
 * 一期用内存计数器即可，重启归 1 后前端会全量重拉，不影响正确性。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class DictService {

    /** 版本号初始值 */
    private static final long INITIAL_VERSION = 1L;

    /** 字典类型表数据访问 */
    private final SysDictTypeMapper sysDictTypeMapper;
    /** 字典数据表数据访问 */
    private final SysDictDataMapper sysDictDataMapper;

    /** 字典版本号，每次写操作递增 */
    private final AtomicLong versionCounter = new AtomicLong(INITIAL_VERSION);

    public DictService(SysDictTypeMapper sysDictTypeMapper, SysDictDataMapper sysDictDataMapper) {
        this.sysDictTypeMapper = sysDictTypeMapper;
        this.sysDictDataMapper = sysDictDataMapper;
    }

    /**
     * 当前字典版本号。
     *
     * @return 版本号
     */
    public long getVersion() {
        return versionCounter.get();
    }

    /**
     * 全量字典，按字典编码分组，只含启用项。
     *
     * @return 字典编码 → 字典项列表
     */
    public Map<String, List<DictItemVO>> listAllDicts() {
        LambdaQueryWrapper<SysDictDataDO> wrapper = Wrappers.lambdaQuery(SysDictDataDO.class);
        wrapper.eq(SysDictDataDO::getStatus, RecordStatusEnum.ACTIVE.getCode());
        wrapper.eq(SysDictDataDO::getIsDeleted, SystemConstants.FLAG_NO);
        wrapper.orderByAsc(SysDictDataDO::getDictType);
        wrapper.orderByAsc(SysDictDataDO::getSort);

        Map<String, List<DictItemVO>> result = new LinkedHashMap<>();
        for (SysDictDataDO data : sysDictDataMapper.selectList(wrapper)) {
            result.computeIfAbsent(data.getDictType(), key -> new ArrayList<>())
                    .add(toDictItem(data));
        }
        return result;
    }

    /**
     * 单个字典的启用项列表。编码不存在时返回空列表，前端下拉框照常渲染。
     *
     * @param code 字典编码
     * @return 字典项列表
     */
    public List<DictItemVO> listDictByCode(String code) {
        LambdaQueryWrapper<SysDictDataDO> wrapper = Wrappers.lambdaQuery(SysDictDataDO.class);
        wrapper.eq(SysDictDataDO::getDictType, code);
        wrapper.eq(SysDictDataDO::getStatus, RecordStatusEnum.ACTIVE.getCode());
        wrapper.eq(SysDictDataDO::getIsDeleted, SystemConstants.FLAG_NO);
        wrapper.orderByAsc(SysDictDataDO::getSort);

        List<SysDictDataDO> dataList = sysDictDataMapper.selectList(wrapper);
        List<DictItemVO> items = new ArrayList<>(dataList.size());
        for (SysDictDataDO data : dataList) {
            items.add(toDictItem(data));
        }
        return items;
    }

    /**
     * 分页查字典类型。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param name     名称关键字，可为空
     * @param code     编码关键字，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    public PageResult<SysDictTypeDO> listDictTypes(long pageNo, long pageSize, String name,
                                                 String code, String status) {
        LambdaQueryWrapper<SysDictTypeDO> wrapper = Wrappers.lambdaQuery(SysDictTypeDO.class);
        wrapper.eq(SysDictTypeDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(name)) {
            wrapper.like(SysDictTypeDO::getDictName, name);
        }
        if (isNotBlank(code)) {
            wrapper.like(SysDictTypeDO::getDictType, code);
        }
        if (isNotBlank(status)) {
            wrapper.eq(SysDictTypeDO::getStatus, status);
        }
        wrapper.orderByAsc(SysDictTypeDO::getDictType);

        Page<SysDictTypeDO> page =
                sysDictTypeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 判断字典编码是否已存在。
     *
     * @param code      字典编码
     * @param excludeId 编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countDictTypesByCode(String code, String excludeId) {
        LambdaQueryWrapper<SysDictTypeDO> wrapper = Wrappers.lambdaQuery(SysDictTypeDO.class);
        wrapper.eq(SysDictTypeDO::getDictType, code);
        wrapper.eq(SysDictTypeDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysDictTypeDO::getId, excludeId);
        }
        return sysDictTypeMapper.selectCount(wrapper);
    }

    /**
     * 新建字典类型。
     *
     * @param request 字典类型信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDictType(DictTypeSaveDTO request) {
        if (countDictTypesByCode(request.getCode(), null) > 0) {
            throw new BizException("字典编码已存在：" + request.getCode());
        }
        SysDictTypeDO dictType = new SysDictTypeDO();
        dictType.setId(IdUtils.nextId());
        dictType.setDictType(request.getCode());
        dictType.setDictName(request.getName());
        dictType.setRemark(request.getRemark());
        dictType.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        dictType.setIsDeleted(SystemConstants.FLAG_NO);
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        dictType.setCreatedAt(now);
        dictType.setUpdatedAt(now);
        sysDictTypeMapper.insert(dictType);
        versionCounter.incrementAndGet();
    }

    /**
     * 更新字典类型。改编码要级联更新其下全部字典项，否则子项会变成孤儿数据。
     *
     * @param id      字典类型 ID
     * @param request 字典类型信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDictType(String id, DictTypeSaveDTO request) {
        SysDictTypeDO dictType = sysDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BizException("字典类型不存在");
        }

        boolean codeChanged = isNotBlank(request.getCode())
                && !request.getCode().equals(dictType.getDictType());
        if (codeChanged) {
            if (countDictTypesByCode(request.getCode(), id) > 0) {
                throw new BizException("字典编码已存在：" + request.getCode());
            }
            String oldCode = dictType.getDictType();
            dictType.setDictType(request.getCode());
            cascadeUpdateDictType(oldCode, request.getCode());
        }

        if (isNotBlank(request.getName())) {
            dictType.setDictName(request.getName());
        }
        if (request.getRemark() != null) {
            dictType.setRemark(request.getRemark());
        }
        if (isNotBlank(request.getStatus())) {
            dictType.setStatus(request.getStatus());
        }
        dictType.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysDictTypeMapper.updateById(dictType);
        versionCounter.incrementAndGet();
    }

    /**
     * 删除字典类型（逻辑删除），同时级联逻辑删除其下全部字典项。
     *
     * @param id 字典类型 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictType(String id) {
        SysDictTypeDO dictType = sysDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BizException("字典类型不存在");
        }
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        dictType.setIsDeleted(SystemConstants.FLAG_YES);
        dictType.setUpdatedAt(now);
        sysDictTypeMapper.updateById(dictType);

        LambdaQueryWrapper<SysDictDataDO> wrapper = Wrappers.lambdaQuery(SysDictDataDO.class);
        wrapper.eq(SysDictDataDO::getDictType, dictType.getDictType());
        for (SysDictDataDO data : sysDictDataMapper.selectList(wrapper)) {
            data.setIsDeleted(SystemConstants.FLAG_YES);
            data.setUpdatedAt(now);
            sysDictDataMapper.updateById(data);
        }
        versionCounter.incrementAndGet();
    }

    /**
     * 分页查字典数据。dictCode 必填，缺失直接拒绝。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param dictCode 字典编码
     * @param label    标签关键字，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    public PageResult<SysDictDataDO> listDictData(long pageNo, long pageSize, String dictCode,
                                                String label, String status) {
        if (!isNotBlank(dictCode)) {
            throw new BizException("dictCode is required");
        }
        LambdaQueryWrapper<SysDictDataDO> wrapper = Wrappers.lambdaQuery(SysDictDataDO.class);
        wrapper.eq(SysDictDataDO::getDictType, dictCode);
        wrapper.eq(SysDictDataDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(label)) {
            wrapper.like(SysDictDataDO::getDictLabel, label);
        }
        if (isNotBlank(status)) {
            wrapper.eq(SysDictDataDO::getStatus, status);
        }
        wrapper.orderByAsc(SysDictDataDO::getSort);

        Page<SysDictDataDO> page =
                sysDictDataMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 新建字典数据。
     *
     * @param request 字典数据信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDictData(DictDataSaveDTO request) {
        SysDictDataDO data = new SysDictDataDO();
        data.setId(IdUtils.nextId());
        data.setDictType(request.getDictCode());
        data.setDictLabel(request.getLabel());
        data.setDictValue(request.getValue());
        data.setCssClass(request.getColor());
        data.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        data.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        data.setRemark(request.getRemark());
        data.setIsDeleted(SystemConstants.FLAG_NO);
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        data.setCreatedAt(now);
        data.setUpdatedAt(now);
        sysDictDataMapper.insert(data);
        versionCounter.incrementAndGet();
    }

    /**
     * 更新字典数据。
     *
     * @param id      字典数据 ID
     * @param request 字典数据信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDictData(String id, DictDataSaveDTO request) {
        SysDictDataDO data = sysDictDataMapper.selectById(id);
        if (data == null) {
            throw new BizException("字典数据不存在");
        }
        if (isNotBlank(request.getDictCode())) {
            data.setDictType(request.getDictCode());
        }
        if (isNotBlank(request.getLabel())) {
            data.setDictLabel(request.getLabel());
        }
        if (isNotBlank(request.getValue())) {
            data.setDictValue(request.getValue());
        }
        if (request.getSort() != null) {
            data.setSort(request.getSort());
        }
        if (isNotBlank(request.getStatus())) {
            data.setStatus(request.getStatus());
        }
        if (request.getColor() != null) {
            data.setCssClass(request.getColor());
        }
        if (request.getRemark() != null) {
            data.setRemark(request.getRemark());
        }
        data.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysDictDataMapper.updateById(data);
        versionCounter.incrementAndGet();
    }

    /**
     * 删除字典数据（逻辑删除）。
     *
     * @param id 字典数据 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictData(String id) {
        SysDictDataDO data = sysDictDataMapper.selectById(id);
        if (data == null) {
            throw new BizException("字典数据不存在");
        }
        data.setIsDeleted(SystemConstants.FLAG_YES);
        data.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysDictDataMapper.updateById(data);
        versionCounter.incrementAndGet();
    }

    /**
     * 字典编码变更时，把其下全部字典项迁移到新编码。
     *
     * @param oldCode 旧编码
     * @param newCode 新编码
     */
    private void cascadeUpdateDictType(String oldCode, String newCode) {
        LambdaQueryWrapper<SysDictDataDO> wrapper = Wrappers.lambdaQuery(SysDictDataDO.class);
        wrapper.eq(SysDictDataDO::getDictType, oldCode);
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        for (SysDictDataDO data : sysDictDataMapper.selectList(wrapper)) {
            data.setDictType(newCode);
            data.setUpdatedAt(now);
            sysDictDataMapper.updateById(data);
        }
    }

    /**
     * 字典行转前端字典项。
     *
     * @param data 字典行
     * @return 字典项
     */
    private DictItemVO toDictItem(SysDictDataDO data) {
        // cssClass 存的是前端语义色，对外输出时字段名对齐前端契约叫 color
        return new DictItemVO(data.getDictLabel(), data.getDictValue(), data.getCssClass());
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param text 待判断字符串
     * @return true 表示非 null 且含非空白字符
     */
    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
