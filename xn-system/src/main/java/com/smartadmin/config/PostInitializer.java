package com.smartadmin.config;

import com.smartadmin.entity.SysPost;
import com.smartadmin.repository.SysPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(5)
@RequiredArgsConstructor
public class PostInitializer implements CommandLineRunner {

    private final SysPostRepository postRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensure("ceo", "董事长", 1, true);
        ensure("manager", "部门经理", 2, true);
        ensure("lead", "项目主管", 3, true);
        ensure("staff", "普通员工", 4, true);
    }

    private void ensure(String code, String name, int sort, boolean builtIn) {
        if (postRepository.existsByCode(code)) {
            return;
        }
        SysPost post = new SysPost();
        post.setCode(code);
        post.setName(name);
        post.setSort(sort);
        post.setStatus(1);
        post.setBuiltIn(builtIn);
        post.setRemark("系统内置岗位");
        postRepository.save(post);
    }
}
