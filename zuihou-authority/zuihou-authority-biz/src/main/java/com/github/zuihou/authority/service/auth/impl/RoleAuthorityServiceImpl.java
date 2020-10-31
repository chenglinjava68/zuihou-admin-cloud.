package com.github.zuihou.authority.service.auth.impl;

import cn.hutool.core.convert.Convert;
import com.github.zuihou.authority.dao.auth.RoleAuthorityMapper;
import com.github.zuihou.authority.dto.auth.RoleAuthoritySaveDTO;
import com.github.zuihou.authority.dto.auth.UserRoleSaveDTO;
import com.github.zuihou.authority.entity.auth.RoleAuthority;
import com.github.zuihou.authority.entity.auth.UserRole;
import com.github.zuihou.authority.enumeration.auth.AuthorizeType;
import com.github.zuihou.authority.service.auth.ResourceService;
import com.github.zuihou.authority.service.auth.RoleAuthorityService;
import com.github.zuihou.authority.service.auth.UserRoleService;
import com.github.zuihou.base.service.SuperServiceImpl;
import com.github.zuihou.common.constant.CacheKey;
import com.github.zuihou.database.mybatis.conditions.Wraps;
import lombok.extern.slf4j.Slf4j;
import net.oschina.j2cache.CacheChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 业务实现类
 * 角色的资源
 * </p>
 *
 * @author zuihou
 * @date 2019-07-03
 */
@Slf4j
@Service

public class RoleAuthorityServiceImpl extends SuperServiceImpl<RoleAuthorityMapper, RoleAuthority> implements RoleAuthorityService {

    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private CacheChannel cache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUserRole(UserRoleSaveDTO userRole) {
        userRoleService.remove(Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, userRole.getRoleId()));
        List<UserRole> list = userRole.getUserIdList()
                .stream()
                .map((userId) -> UserRole.builder()
                        .userId(userId)
                        .roleId(userRole.getRoleId())
                        .build())
                .collect(Collectors.toList());
        userRoleService.saveBatch(list);

        //清除 用户拥有的菜单和资源列表
        userRole.getUserIdList().forEach((userId) -> {
            String key = key(userId);
            cache.evict(CacheKey.USER_RESOURCE, key);
            cache.evict(CacheKey.USER_MENU, key);
        });
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveRoleAuthority(RoleAuthoritySaveDTO dto) {
        //删除角色和资源的关联
        super.remove(Wraps.<RoleAuthority>lbQ().eq(RoleAuthority::getRoleId, dto.getRoleId()));

        List<RoleAuthority> list = new ArrayList<>();
        if (dto.getResourceIdList() != null && !dto.getResourceIdList().isEmpty()) {
            List<Long> menuIdList = resourceService.findMenuIdByResourceId(dto.getResourceIdList());
            if (dto.getMenuIdList() == null || dto.getMenuIdList().isEmpty()) {
                dto.setMenuIdList(menuIdList);
            } else {
                dto.getMenuIdList().addAll(menuIdList);
            }

            //保存授予的资源
            List<RoleAuthority> resourceList = new HashSet<>(dto.getResourceIdList())
                    .stream()
                    .map((resourceId) -> RoleAuthority.builder()
                            .authorityType(AuthorizeType.RESOURCE)
                            .authorityId(resourceId)
                            .roleId(dto.getRoleId())
                            .build())
                    .collect(Collectors.toList());
            list.addAll(resourceList);
        }
        if (dto.getMenuIdList() != null && !dto.getMenuIdList().isEmpty()) {
            //保存授予的菜单
            List<RoleAuthority> menuList = new HashSet<>(dto.getMenuIdList())
                    .stream()
                    .map((menuId) -> RoleAuthority.builder()
                            .authorityType(AuthorizeType.MENU)
                            .authorityId(menuId)
                            .roleId(dto.getRoleId())
                            .build())
                    .collect(Collectors.toList());
            list.addAll(menuList);
        }
        super.saveBatch(list);

        // 清理
        List<Long> userIdList = userRoleService.listObjs(Wraps.<UserRole>lbQ().select(UserRole::getUserId).eq(UserRole::getRoleId, dto.getRoleId()),
                (userId) -> Convert.toLong(userId, 0L));
        userIdList.stream().collect(Collectors.toSet()).forEach(userId -> {
            log.info("清理了 {} 的菜单/资源", userId);
            cache.evict(CacheKey.USER_RESOURCE, key(userId));
            cache.evict(CacheKey.USER_MENU, key(userId));
            cache.evict(CacheKey.USER_ROLE, key(userId));
        });

        cache.evict(CacheKey.ROLE_RESOURCE, key(dto.getRoleId()));
        cache.evict(CacheKey.ROLE_MENU, key(dto.getRoleId()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByAuthorityId(List<Long> ids) {
        return remove(Wraps.<RoleAuthority>lbQ().in(RoleAuthority::getAuthorityId, ids));
    }
}
