package com.cim.reqflow.controller;

import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.dto.DictDataSaveDTO;
import com.cim.reqflow.system.dto.DictTypeSaveDTO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysDictTypeDO;
import com.cim.reqflow.system.service.DictService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理 - 字典类型与字典数据的完整 CRUD。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/dict")
public class SystemDictController {

    /** 字典业务服务 */
    private final DictService dictService;

    public SystemDictController(DictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 分页查字典类型。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param name     名称关键字，可为空
     * @param code     编码关键字，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    @GetMapping("/type/list")
    public R<PageResult<SysDictTypeDO>> listTypes(
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_NO_TEXT) long page,
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_SIZE_TEXT) long pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return R.ok(dictService.listDictTypes(page, pageSize, name, code, status));
    }

    /**
     * 判断字典编码是否已存在。
     *
     * @param code 字典编码
     * @param id   编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/type/code-exists")
    public R<Boolean> typeCodeExists(@RequestParam("code") String code,
                                     @RequestParam(required = false) String id) {
        return R.ok(dictService.countDictTypesByCode(code, id) > 0);
    }

    /**
     * 新建字典类型。
     *
     * @param request 字典类型信息
     * @return 空数据响应
     */
    @PostMapping("/type")
    public R<Void> createType(@RequestBody DictTypeSaveDTO request) {
        if (request == null || isBlank(request.getCode()) || isBlank(request.getName())) {
            return R.fail("code 和 name 必填");
        }
        dictService.saveDictType(request);
        return R.ok();
    }

    /**
     * 更新字典类型。
     *
     * @param id      字典类型 ID
     * @param request 字典类型信息
     * @return 空数据响应
     */
    @PutMapping("/type/{id}")
    public R<Void> updateType(@PathVariable String id, @RequestBody DictTypeSaveDTO request) {
        dictService.updateDictType(id, request);
        return R.ok();
    }

    /**
     * 删除字典类型。
     *
     * @param id 字典类型 ID
     * @return 空数据响应
     */
    @DeleteMapping("/type/{id}")
    public R<Void> deleteType(@PathVariable String id) {
        dictService.deleteDictType(id);
        return R.ok();
    }

    /**
     * 分页查字典数据。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param dictCode 字典编码，必填
     * @param label    标签关键字，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    @GetMapping("/data/list")
    public R<PageResult<SysDictDataDO>> listData(
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_NO_TEXT) long page,
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_SIZE_TEXT) long pageSize,
            @RequestParam(required = false) String dictCode,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String status) {
        return R.ok(dictService.listDictData(page, pageSize, dictCode, label, status));
    }

    /**
     * 新建字典数据。
     *
     * @param request 字典数据信息
     * @return 空数据响应
     */
    @PostMapping("/data")
    public R<Void> createData(@RequestBody DictDataSaveDTO request) {
        if (request == null || isBlank(request.getDictCode()) || isBlank(request.getLabel())
                || isBlank(request.getValue())) {
            return R.fail("dictCode、label、value 必填");
        }
        dictService.saveDictData(request);
        return R.ok();
    }

    /**
     * 更新字典数据。
     *
     * @param id      字典数据 ID
     * @param request 字典数据信息
     * @return 空数据响应
     */
    @PutMapping("/data/{id}")
    public R<Void> updateData(@PathVariable String id, @RequestBody DictDataSaveDTO request) {
        dictService.updateDictData(id, request);
        return R.ok();
    }

    /**
     * 删除字典数据。
     *
     * @param id 字典数据 ID
     * @return 空数据响应
     */
    @DeleteMapping("/data/{id}")
    public R<Void> deleteData(@PathVariable String id) {
        dictService.deleteDictData(id);
        return R.ok();
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param text 待判断字符串
     * @return true 表示为 null 或全空白
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
