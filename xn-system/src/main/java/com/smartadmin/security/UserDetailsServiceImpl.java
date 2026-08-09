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
        return toLoginUser(user);
    }

    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByIdWithRoles(userId)
                        .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return toLoginUser(user);
    }

    private LoginUser toLoginUser(User user) {
        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        var roleCodes = permissionRepository.findRoleCodesByUserId(user.getId());
        if (roleCodes.contains(RbacService.SUPER_ADMIN_CODE)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        if (roleCodes.contains("ADMIN")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (roleCodes.contains("GUEST")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_GUEST"));
        }
        permissionRepository
                .findPermissionCodesByUserId(user.getId())
                .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                authorities);
    }
}
