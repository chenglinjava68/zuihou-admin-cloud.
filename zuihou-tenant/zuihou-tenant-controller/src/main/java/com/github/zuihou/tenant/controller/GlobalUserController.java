package com.github.zuihou.tenant.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.zuihou.authority.dto.auth.UserUpdatePasswordDTO;
import com.github.zuihou.authority.entity.auth.User;
import com.github.zuihou.authority.service.auth.UserService;
import com.github.zuihou.base.R;
import com.github.zuihou.base.controller.SuperController;
import com.github.zuihou.base.entity.SuperEntity;
import com.github.zuihou.base.request.PageParams;
import com.github.zuihou.common.constant.BizConstant;
import com.github.zuihou.context.BaseContextHandler;
import com.github.zuihou.database.mybatis.conditions.Wraps;
import com.github.zuihou.database.mybatis.conditions.query.LbqWrapper;
import com.github.zuihou.database.mybatis.conditions.query.QueryWrap;
import com.github.zuihou.log.annotation.SysLog;
import com.github.zuihou.tenant.dto.GlobalUserPageDTO;
import com.github.zuihou.tenant.dto.GlobalUserSaveDTO;
import com.github.zuihou.tenant.dto.GlobalUserUpdateDTO;
import com.github.zuihou.tenant.entity.GlobalUser;
import com.github.zuihou.tenant.entity.Tenant;
import com.github.zuihou.tenant.enumeration.TenantStatusEnum;
import com.github.zuihou.tenant.service.GlobalUserService;
import com.github.zuihou.tenant.service.TenantService;
import com.github.zuihou.utils.BeanPlusUtil;
import com.github.zuihou.utils.BizAssert;
import com.github.zuihou.utils.StrHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * 全局账号
 * </p>
 *
 * @author zuihou
 * @date 2019-10-25
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/globalUser")
@Api(value = "GlobalUser", tags = "全局账号")
@SysLog(enabled = false)
public class GlobalUserController extends SuperController<GlobalUserService, Long, GlobalUser, GlobalUserPageDTO, GlobalUserSaveDTO, GlobalUserUpdateDTO> {

    @Autowired
    private UserService userService;
    @Autowired
    private TenantService tenantService;

    @Override
    public R<GlobalUser> handlerSave(GlobalUserSaveDTO model) {
        if (StrUtil.isEmpty(model.getTenantCode()) || BizConstant.SUPER_TENANT.equals(model.getTenantCode())) {
            return success(baseService.save(model));
        } else {
            Tenant tenant = tenantService.getByCode(model.getTenantCode());
            BizAssert.notNull(tenant, "租户不能为空");
            BizAssert.isTrue(TenantStatusEnum.NORMAL.eq(tenant.getStatus()), StrUtil.format("租户[{}]不可用", tenant.getName()));

            BaseContextHandler.setTenant(model.getTenantCode());
            User user = BeanPlusUtil.toBean(model, User.class);
            user.setName(StrHelper.getOrDef(model.getName(), model.getAccount()));
            if (StrUtil.isEmpty(user.getPassword())) {
                user.setPassword(BizConstant.DEF_PASSWORD);
            }
            user.setStatus(true);
            userService.initUser(user);
            return success(BeanPlusUtil.toBean(user, GlobalUser.class));
        }
    }

    @Override
    public R<GlobalUser> handlerUpdate(GlobalUserUpdateDTO model) {
        if (StrUtil.isEmpty(model.getTenantCode()) || BizConstant.SUPER_TENANT.equals(model.getTenantCode())) {
            return success(baseService.update(model));
        } else {
            BaseContextHandler.setTenant(model.getTenantCode());
            User user = BeanPlusUtil.toBean(model, User.class);
            userService.updateUser(user);
            return success(BeanPlusUtil.toBean(user, GlobalUser.class));
        }
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "tenantCode", value = "企业编码", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "account", value = "账号", dataType = "string", paramType = "query"),
    })
    @ApiOperation(value = "检测账号是否可用", notes = "检测账号是否可用")
    @GetMapping("/check")
    public R<Boolean> check(@RequestParam String tenantCode, @RequestParam String account) {
        if (StrUtil.isEmpty(tenantCode) || BizConstant.SUPER_TENANT.equals(tenantCode)) {
            return success(baseService.check(account));
        } else {
            BaseContextHandler.setTenant(tenantCode);
            return success(userService.check(account));
        }
    }

    @Override
    public void query(PageParams<GlobalUserPageDTO> params, IPage<GlobalUser> page, Long defSize) {
        GlobalUserPageDTO model = params.getModel();
        if (StrUtil.isEmpty(model.getTenantCode()) || BizConstant.SUPER_TENANT.equals(model.getTenantCode())) {
            QueryWrap<GlobalUser> wrapper = handlerWrapper(null, params);
            wrapper.lambda().eq(GlobalUser::getTenantCode, model.getTenantCode())
                    .like(GlobalUser::getAccount, model.getAccount())
                    .like(GlobalUser::getName, model.getName());
            baseService.page(page, wrapper);
            return;
        }
        BaseContextHandler.setTenant(model.getTenantCode());

        IPage<User> userPage = params.buildPage();
        LbqWrapper<User> wrapper = Wraps.lbq(null, params.getMap(), User.class);
        wrapper.like(User::getAccount, model.getAccount())
                .like(User::getName, model.getName())
                .orderByDesc(User::getCreateTime);

        userService.page(userPage, wrapper);

        page.setCurrent(userPage.getCurrent());
        page.setSize(userPage.getSize());
        page.setTotal(userPage.getTotal());
        page.setPages(userPage.getPages());
        List<GlobalUser> list = BeanPlusUtil.toBeanList(userPage.getRecords(), GlobalUser.class);
        page.setRecords(list);
    }


    @ApiOperation(value = "删除")
    @DeleteMapping("/delete")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "tenantCode", value = "企业编码", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "ids[]", value = "主键id", dataType = "array", paramType = "query"),
    })
    public R<Boolean> delete(@RequestParam String tenantCode, @RequestParam("ids[]") List<Long> ids) {
        if (StrUtil.isEmpty(tenantCode) || BizConstant.SUPER_TENANT.equals(tenantCode)) {
            return success(baseService.removeByIds(ids));
        } else {
            BaseContextHandler.setTenant(tenantCode);
            return success(userService.remove(ids));
        }
    }


    /**
     * 修改密码
     *
     * @param model 修改实体
     * @return
     */
    @ApiOperation(value = "修改密码", notes = "修改密码")
    @PutMapping("/reset")
    public R<Boolean> updatePassword(@RequestBody @Validated(SuperEntity.Update.class) UserUpdatePasswordDTO model) {
        if (StrUtil.isEmpty(model.getTenantCode()) || BizConstant.SUPER_TENANT.equals(model.getTenantCode())) {
            return success(baseService.updatePassword(model));
        } else {
            BaseContextHandler.setTenant(model.getTenantCode());
            return success(userService.reset(model));
        }
    }
}
