package com.github.zuihou.tenant.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.github.zuihou.authority.dto.auth.UserUpdatePasswordDTO;
import com.github.zuihou.base.service.SuperServiceImpl;
import com.github.zuihou.database.mybatis.conditions.Wraps;
import com.github.zuihou.tenant.dao.GlobalUserMapper;
import com.github.zuihou.tenant.dto.GlobalUserSaveDTO;
import com.github.zuihou.tenant.dto.GlobalUserUpdateDTO;
import com.github.zuihou.tenant.entity.GlobalUser;
import com.github.zuihou.tenant.service.GlobalUserService;
import com.github.zuihou.utils.BeanPlusUtil;
import com.github.zuihou.utils.BizAssert;
import com.github.zuihou.utils.StrHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.github.zuihou.utils.BizAssert.isFalse;

/**
 * <p>
 * 业务实现类
 * 全局账号
 * </p>
 *
 * @author zuihou
 * @date 2019-10-25
 */
@Slf4j
@Service

public class GlobalUserServiceImpl extends SuperServiceImpl<GlobalUserMapper, GlobalUser> implements GlobalUserService {

    @Override
    public Boolean check(String account) {
        return super.count(Wraps.<GlobalUser>lbQ()
                .eq(GlobalUser::getAccount, account)) > 0;
    }

    /**
     * @param data
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlobalUser save(GlobalUserSaveDTO data) {
        BizAssert.equals(data.getPassword(), data.getConfirmPassword(), "2次输入的密码不一致");
        isFalse(check(data.getAccount()), "账号已经存在");

        String md5Password = SecureUtil.md5(data.getPassword());

        GlobalUser globalAccount = BeanPlusUtil.toBean(data, GlobalUser.class);
        // 全局表就不存用户数据了
        globalAccount.setPassword(md5Password);
        globalAccount.setName(StrHelper.getOrDef(data.getName(), data.getAccount()));
        globalAccount.setReadonly(false);

        save(globalAccount);
        return globalAccount;
    }

    /**
     * @param data
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlobalUser update(GlobalUserUpdateDTO data) {
        GlobalUser globalUser = BeanPlusUtil.toBean(data, GlobalUser.class);
        globalUser.setPassword(null);
        updateById(globalUser);
        return globalUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePassword(UserUpdatePasswordDTO data) {
        BizAssert.equals(data.getConfirmPassword(), data.getPassword(), "密码与确认密码不一致");

        GlobalUser user = getById(data.getId());
        BizAssert.notNull(user, "用户不存在");

        GlobalUser build = GlobalUser.builder().password(SecureUtil.md5(data.getPassword())).id(data.getId()).build();
        return updateById(build);
    }
}
