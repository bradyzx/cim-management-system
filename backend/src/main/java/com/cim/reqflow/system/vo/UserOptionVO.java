package com.cim.reqflow.system.vo;

/**
 * 派单候选人选项。只给下拉框用，字段刻意保持最小。
 * <p>
 * 为什么不复用 {@link UserListVO}：那个对象带手机号、邮箱、角色列表等一整行管理字段，
 * 下拉框一项都显示不到，返回去只是把人员信息多暴露一遍。
 *
 * @author Haijie Xiao
 * @date 2026-11-06
 */
public class UserOptionVO {

    /** 用户 ID，派单时作为 assigneeId 提交 */
    private String id;

    /** 真实姓名，下拉框显示的主文本 */
    private String realName;

    /** 主部门名称，下拉框显示在姓名后面便于区分同名 */
    private String deptName;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }
}
