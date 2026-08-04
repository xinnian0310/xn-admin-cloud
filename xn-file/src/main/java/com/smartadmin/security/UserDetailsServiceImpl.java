package com.smartadmin.security;

import com.smartadmin.entity.User;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.service.RbacService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByUsernameWithRolesIgnoreCase(username)
                        .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        boolean isSuperAdmin =
                permissionRepository
                        .findRoleCodesByUserId(user.getId())
                        .contains(RbacService.SUPER_ADMIN_CODE);
        if (isSuperAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        permissionRepository
                .findPermissionCodesByUserId(user.getId())
                .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                true,
                true,
                true,
                authorities);
    }
}
