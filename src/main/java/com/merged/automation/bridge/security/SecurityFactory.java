package com.merged.automation.bridge.security;

public class SecurityFactory {
    
    public static SecurityComponents createSecurityComponents() {
        SecurityConfig securityConfig = new SecurityConfig();
        
        JwtService jwtService = new JwtService();
        setSecurityConfig(jwtService, securityConfig);
        
        IpWhitelistService ipWhitelistService = new IpWhitelistService();
        setSecurityConfig(ipWhitelistService, securityConfig);
        
        RateLimitService rateLimitService = new RateLimitService();
        setSecurityConfig(rateLimitService, securityConfig);
        
        ValidationService validationService = new ValidationService();
        
        SecurityManager securityManager = new SecurityManager();
        setSecurityConfig(securityManager, securityConfig);
        setJwtService(securityManager, jwtService);
        setIpWhitelistService(securityManager, ipWhitelistService);
        setRateLimitService(securityManager, rateLimitService);
        setValidationService(securityManager, validationService);
        
        return new SecurityComponents(
            securityConfig,
            jwtService,
            ipWhitelistService,
            rateLimitService,
            validationService,
            securityManager
        );
    }
    
    private static void setSecurityConfig(Object target, SecurityConfig securityConfig) {
        try {
            var field = target.getClass().getDeclaredField("securityConfig");
            field.setAccessible(true);
            field.set(target, securityConfig);
        } catch (Exception e) {
            // Field might not exist
        }
    }
    
    private static void setJwtService(Object target, JwtService jwtService) {
        try {
            var field = target.getClass().getDeclaredField("jwtService");
            field.setAccessible(true);
            field.set(target, jwtService);
        } catch (Exception e) {
            // Field might not exist
        }
    }
    
    private static void setIpWhitelistService(Object target, IpWhitelistService ipWhitelistService) {
        try {
            var field = target.getClass().getDeclaredField("ipWhitelistService");
            field.setAccessible(true);
            field.set(target, ipWhitelistService);
        } catch (Exception e) {
            // Field might not exist
        }
    }
    
    private static void setRateLimitService(Object target, RateLimitService rateLimitService) {
        try {
            var field = target.getClass().getDeclaredField("rateLimitService");
            field.setAccessible(true);
            field.set(target, rateLimitService);
        } catch (Exception e) {
            // Field might not exist
        }
    }
    
    private static void setValidationService(Object target, ValidationService validationService) {
        try {
            var field = target.getClass().getDeclaredField("validationService");
            field.setAccessible(true);
            field.set(target, validationService);
        } catch (Exception e) {
            // Field might not exist
        }
    }
    
    public static class SecurityComponents {
        public final SecurityConfig securityConfig;
        public final JwtService jwtService;
        public final IpWhitelistService ipWhitelistService;
        public final RateLimitService rateLimitService;
        public final ValidationService validationService;
        public final SecurityManager securityManager;
        
        public SecurityComponents(SecurityConfig securityConfig,
                                JwtService jwtService,
                                IpWhitelistService ipWhitelistService,
                                RateLimitService rateLimitService,
                                ValidationService validationService,
                                SecurityManager securityManager) {
            this.securityConfig = securityConfig;
            this.jwtService = jwtService;
            this.ipWhitelistService = ipWhitelistService;
            this.rateLimitService = rateLimitService;
            this.validationService = validationService;
            this.securityManager = securityManager;
        }
    }
}