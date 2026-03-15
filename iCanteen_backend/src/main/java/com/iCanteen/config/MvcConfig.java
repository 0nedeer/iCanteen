package com.iCanteen.config;

import com.iCanteen.utils.LoginInterceptor;
import com.iCanteen.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/canteen/list",
                        "/canteen/*",
                        "/canteen/*/dishes",
                        "/canteen/*/windows",
                        "/window/list",
                        "/window/*",
                        "/window/*/dishes",
                        "/dish/by-window",
                        "/dish/by-canteen",
                        "/dish/recommend/by-canteen",
                        "/dish/random-recommend",
                        "/dish/*"
                )
                .order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }
}
