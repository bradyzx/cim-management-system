package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.system.service.DictService;
import com.cim.reqflow.system.vo.DictItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字典三件套：全量 / 单个 / 版本号。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/dict")
public class DictController {

    /** 字典业务服务 */
    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 全量字典，按编码分组，前端本地缓存。
     *
     * @return 字典编码 → 字典项列表
     */
    @GetMapping("/all")
    public R<Map<String, List<DictItemVO>>> listAll() {
        return R.ok(dictService.listAllDicts());
    }

    /**
     * 单个字典的启用项，编码不存在返回空列表。
     *
     * @param code 字典编码
     * @return 字典项列表
     */
    @GetMapping("/data")
    public R<List<DictItemVO>> listByCode(@RequestParam("code") String code) {
        return R.ok(dictService.listDictByCode(code));
    }

    /**
     * 版本号：字典增删改后 +1，前端轮询比对决定是否重拉全量。
     *
     * @return 版本号
     */
    @GetMapping("/version")
    public R<Long> version() {
        return R.ok(dictService.getVersion());
    }
}
