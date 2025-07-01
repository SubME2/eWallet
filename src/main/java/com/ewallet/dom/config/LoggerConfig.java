package com.ewallet.dom.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Aspect
@Component
public class LoggerConfig {

    @Around("@annotation(com.ewallet.dom.util.LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object proceed = joinPoint.proceed();
        long endTime = System.currentTimeMillis();
        log.info("{} executed in {}ms", joinPoint.getSignature(), endTime - startTime);
        return proceed;
    }

    @Before("@annotation(com.ewallet.dom.util.LogExecution)")
    public void logBefore(JoinPoint joinPoint) {
        // Log method entry for controllers
        Object[] args = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String parameters = (String)((Stream) Arrays.stream(args).sequential()).map(Object::toString).collect(Collectors.joining());
        log.info("Entering method: {}.{} with parameters: {}", className, methodName, parameters);
    }

    @After("@annotation(com.ewallet.dom.util.LogExecution)")
    public void logAfter(JoinPoint joinPoint) {
        // Log method exit for controllers
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        log.info("Exiting method: {}.{}", className, methodName);
    }
}
