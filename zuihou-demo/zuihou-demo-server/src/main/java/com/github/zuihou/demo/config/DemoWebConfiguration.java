package com.github.zuihou.demo.config;

import com.github.zuihou.boot.config.BaseConfig;
import com.github.zuihou.log.event.SysLogListener;
import com.github.zuihou.oauth.api.LogApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zuihou
 * @createTime 2017-12-15 14:42
 */
@Configuration
public class DemoWebConfiguration extends BaseConfig {
    @Bean
    @ConditionalOnExpression("${zuihou.log.enabled:true} && 'DB'.equals('${zuihou.log.type:LOGGER}')")
    public SysLogListener sysLogListener(LogApi logApi) {
        return new SysLogListener((log) -> logApi.save(log));
    }
}
