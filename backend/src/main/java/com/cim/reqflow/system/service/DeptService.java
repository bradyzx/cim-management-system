package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.dto.DeptSaveDTO;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.entity.SysUserDeptDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysUserDeptMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 部门管理：树形结构的增删改查。
 * ancestors 存「从根到父」的 ID 路径串（如 /1/5/），靠它一次算出层级与子树范围；
 * 改父级需要重算整棵子树，因此本期不允许改父级。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class DeptService {

    /** 根部门的 ancestors 值 */
    private static final String ROOT_ANCESTORS = "/";

    /** 路径分隔符 */
    private static final String PATH_SEPARATOR = "/";

    /** 根部门层级 */
    private static final int ROOT_LEVEL = 1;

    /** 部门表数据访问 */
    private final SysDeptMapper sysDeptMapper;
    /** 用户表数据访问 */
    private final SysUserMapper sysUserMapper;
    /** 用户部门关系表数据访问 */
    private final SysUserDeptMapper sysUserDeptMapper;

    public DeptService(SysDeptMapper sysDeptMapper, SysUserMapper sysUserMapper,
                       SysUserDeptMapper sysUserDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserDeptMapper = sysUserDeptMapper;
    }

    /**
     * 平铺返回全部部门，按 ancestors 字典序即树序，前端按缩进展示或自行组树。
     *
     * @param keyword 关键字，同时匹配名称与编码，可为空
     * @return 部门列表
     */
    public List<SysDeptDO> listDepts(String keyword) {
        LambdaQueryWrapper<SysDeptDO> wrapper = Wrappers.lambdaQuery(SysDeptDO.class);
        if (isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysDeptDO::getDeptName, keyword)
                    .or()
                    .like(SysDeptDO::getDeptCode, keyword));
        }
        List<SysDeptDO> depts = sysDeptMapper.selectList(wrapper);
        depts.sort(Comparator.comparing(SysDeptDO::getAncestors).thenComparing(SysDeptDO::getSort));
        return depts;
    }

    /**
     * 判断部门编码是否已存在。
     *
     * @param deptCode  部门编码
     * @param excludeId 编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countDeptsByCode(String deptCode, String excludeId) {
        LambdaQueryWrapper<SysDeptDO> wrapper = Wrappers.lambdaQuery(SysDeptDO.class);
        wrapper.eq(SysDeptDO::getDeptCode, deptCode);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysDeptDO::getId, excludeId);
        }
        return sysDeptMapper.selectCount(wrapper);
    }

    /**
     * 新增部门。根据上级部门自动推算 ancestors 与层级。
     *
     * @param request 部门信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDept(DeptSaveDTO request) {
        if (countDeptsByCode(request.getDeptCode(), null) > 0) {
            throw new BizException("部门编码已存在：" + request.getDeptCode());
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        SysDeptDO dept = new SysDeptDO();
        dept.setId(IdUtils.nextId());
        dept.setDeptCode(request.getDeptCode());
        dept.setDeptName(request.getDeptName());
        dept.setParentId(request.getParentId());
        if (isNotBlank(request.getParentId())) {
            SysDeptDO parentDept = sysDeptMapper.selectById(request.getParentId());
            if (parentDept == null) {
                throw new BizException("上级部门不存在");
            }
            dept.setAncestors(parentDept.getAncestors() + parentDept.getId() + PATH_SEPARATOR);
            int parentLevel = parentDept.getDeptLevel() == null
                    ? ROOT_LEVEL : parentDept.getDeptLevel();
            dept.setDeptLevel(parentLevel + 1);
        } else {
            // 根部门没有父级：ancestors 就是 /，层级 1
            dept.setAncestors(ROOT_ANCESTORS);
            dept.setDeptLevel(ROOT_LEVEL);
        }
        dept.setLeaderUserId(request.getLeaderUserId());
        dept.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        dept.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        dept.setRemark(request.getRemark());
        dept.setSyncSource(SystemConstants.SYNC_SOURCE_LOCAL);
        dept.setCreatedAt(now);
        dept.setUpdatedAt(now);
        sysDeptMapper.insert(dept);
    }

    /**
     * 修改部门。不支持改上级部门。
     *
     * @param id      部门 ID
     * @param request 部门信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(String id, DeptSaveDTO request) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("部门不存在");
        }

        boolean codeChanged = isNotBlank(request.getDeptCode())
                && !request.getDeptCode().equals(dept.getDeptCode());
        if (codeChanged && countDeptsByCode(request.getDeptCode(), id) > 0) {
            throw new BizException("部门编码已存在：" + request.getDeptCode());
        }
        if (codeChanged) {
            dept.setDeptCode(request.getDeptCode());
        }
        if (isNotBlank(request.getDeptName())) {
            dept.setDeptName(request.getDeptName());
        }
        if (request.getLeaderUserId() != null) {
            dept.setLeaderUserId(isNotBlank(request.getLeaderUserId())
                    ? request.getLeaderUserId() : null);
        }
        if (request.getSort() != null) {
            dept.setSort(request.getSort());
        }
        if (isNotBlank(request.getStatus())) {
            dept.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            dept.setRemark(request.getRemark());
        }
        dept.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysDeptMapper.updateById(dept);
    }

    /**
     * 删除部门。子部门、部门下用户、用户部门关系三者任一存在都不允许删除，
     * 否则会留下孤儿引用。
     *
     * @param id 部门 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(String id) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("部门不存在");
        }

        LambdaQueryWrapper<SysDeptDO> childWrapper = Wrappers.lambdaQuery(SysDeptDO.class);
        childWrapper.eq(SysDeptDO::getParentId, id);
        if (sysDeptMapper.selectCount(childWrapper) > 0) {
            throw new BizException("该部门下还有子部门，请先删除或调整子部门");
        }

        LambdaQueryWrapper<SysUserDO> userWrapper = Wrappers.lambdaQuery(SysUserDO.class);
        userWrapper.eq(SysUserDO::getPrimaryDeptId, id);
        if (sysUserMapper.selectCount(userWrapper) > 0) {
            throw new BizException("该部门下还有用户，请先调整用户主部门");
        }

        LambdaQueryWrapper<SysUserDeptDO> relationWrapper =
                Wrappers.lambdaQuery(SysUserDeptDO.class);
        relationWrapper.eq(SysUserDeptDO::getDeptId, id);
        if (sysUserDeptMapper.selectCount(relationWrapper) > 0) {
            throw new BizException("该部门仍被用户部门关系引用，请先在用户管理中解除");
        }

        // sys_dept 没有逻辑删除字段，这里做物理删；上面的校验已确保不会留下孤儿引用
        sysDeptMapper.deleteById(id);
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
