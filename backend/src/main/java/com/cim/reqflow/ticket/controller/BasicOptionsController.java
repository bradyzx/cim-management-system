package com.cim.reqflow.ticket.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.R;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqSystemDO;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqSystemMapper;
import com.cim.reqflow.ticket.vo.BasicOptionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础数据下拉：项目与系统。
 * <p>
 * 供建单表单做两级联动：先拉项目，选中项目后按 projectId 拉该项目下的系统。
 * 一期只读；项目/系统的新增维护在系统管理页（未实现），数据来自种子与后续维护。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@RestController
@RequestMapping("/basic")
public class BasicOptionsController {

    /** 项目数据访问 */
    private final ReqProjectMapper reqProjectMapper;

    /** 系统数据访问 */
    private final ReqSystemMapper reqSystemMapper;

    public BasicOptionsController(ReqProjectMapper reqProjectMapper,
                                  ReqSystemMapper reqSystemMapper) {
        this.reqProjectMapper = reqProjectMapper;
        this.reqSystemMapper = reqSystemMapper;
    }

    /**
     * 项目下拉。已关闭（CLOSED）的项目不出现在建单入口里。
     *
     * @return 项目列表，按排序号升序
     */
    @GetMapping("/projects")
    public R<List<BasicOptionVO>> projects() {
        List<ReqProjectDO> projects = reqProjectMapper.selectList(
                Wrappers.<ReqProjectDO>lambdaQuery()
                        .ne(ReqProjectDO::getStatus, "CLOSED")
                        .orderByAsc(ReqProjectDO::getSortNo));
        List<BasicOptionVO> result = new ArrayList<>(projects.size());
        for (ReqProjectDO project : projects) {
            BasicOptionVO vo = new BasicOptionVO();
            vo.setId(project.getId());
            vo.setCode(project.getProjectCode());
            vo.setName(project.getProjectName());
            result.add(vo);
        }
        return R.ok(result);
    }

    /**
     * 系统下拉，按项目过滤。不传 projectId 返回空 ——
     * 逼着前端先选项目，避免提出人选到与项目无关的系统。
     *
     * @param projectId 项目 ID
     * @return 该项目下的系统列表
     */
    @GetMapping("/systems")
    public R<List<BasicOptionVO>> systems(@RequestParam String projectId) {
        List<ReqSystemDO> systems = reqSystemMapper.selectList(
                Wrappers.<ReqSystemDO>lambdaQuery()
                        .eq(ReqSystemDO::getProjectId, projectId)
                        .orderByAsc(ReqSystemDO::getSortNo));
        List<BasicOptionVO> result = new ArrayList<>(systems.size());
        for (ReqSystemDO system : systems) {
            BasicOptionVO vo = new BasicOptionVO();
            vo.setId(system.getId());
            vo.setCode(system.getSystemCode());
            vo.setName(system.getSystemName());
            vo.setProjectId(system.getProjectId());
            result.add(vo);
        }
        return R.ok(result);
    }
}
