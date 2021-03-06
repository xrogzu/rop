/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rop.impl;

import com.rop.Interceptor;
import com.rop.RopException;
import com.rop.RopMarshaller;
import com.rop.ServiceRouter;
import com.rop.ThreadFerry;
import com.rop.config.InterceptorHolder;
import com.rop.config.RopEventListenerHodler;
import com.rop.event.RopEvent;
import com.rop.event.RopEventListener;
import com.rop.security.*;
import com.rop.security.SecurityManager;
import com.rop.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <pre>
 * 功能说明：
 * </pre>
 *
 * @author 陈雄华
 * @version 1.0
 */
public class AnnotationServletServiceRouterFactoryBean
        implements FactoryBean<ServiceRouter>,ApplicationContextAware, InitializingBean, DisposableBean{

    private static final String ALL_FILE_TYPES = "*";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private ApplicationContext applicationContext;

    private ThreadPoolExecutor threadPoolExecutor;

    private SessionManager sessionManager;

    private AppSecretManager appSecretManager;

    private ServiceAccessController serviceAccessController;

    private InvokeTimesController invokeTimesController;

    private boolean signEnable = true;

    private String extErrorBasename;

    private String[] extErrorBasenames;

    private int serviceTimeoutSeconds = -1;

    private Class<? extends ThreadFerry> threadFerryClass = DumbThreadFerry.class;

    private FormattingConversionService formattingConversionService;

    private ServiceRouter serviceRouter;

    //多值用逗号分隔,默认支持4种格式的文件
    private String uploadFileTypes = ALL_FILE_TYPES;

    //单位为K，默认为10M
    private int uploadFileMaxSize = 10 * 1024;

    private RopMarshaller xmlMarshaller;

    private RopMarshaller jsonMarshaller;
    
    private SecurityManager securityManager;

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void destroy() throws Exception {
        serviceRouter.shutdown();
    }

    public Class<?> getObjectType() {
        return serviceRouter == null ? ServiceRouter.class : serviceRouter.getClass();
    }

    public ServiceRouter getObject() throws Exception {
        return this.serviceRouter;
    }

    public boolean isSingleton() {
        return true;
    }

    public void setInvokeTimesController(InvokeTimesController invokeTimesController) {
        this.invokeTimesController = invokeTimesController;
    }

    public void setThreadFerryClass(Class<? extends ThreadFerry> threadFerryClass) {
        this.threadFerryClass = threadFerryClass;
    }

    @SuppressWarnings("unchecked")
	public void setThreadFerryClassName(String threadFerryClassName) {
        try {
            if (StringUtils.hasText(threadFerryClassName)) {
                Class<?> threadFerryClass =
                        ClassUtils.forName(threadFerryClassName, getClass().getClassLoader());
                if (!ClassUtils.isAssignable(ThreadFerry.class, threadFerryClass)) {
                    throw new RopException(threadFerryClassName + "没有实现"
                                         + ThreadFerry.class.getName() + "接口");
                }
                this.threadFerryClass = (Class<? extends ThreadFerry>)threadFerryClass;
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

	public void afterPropertiesSet() throws Exception {
    	if(serviceRouter == null){
    		serviceRouter = new AnnotationServletServiceRouter();
    	}
        //设置国际化错误资源
        if (extErrorBasename != null) {
            serviceRouter.setExtErrorBasename(extErrorBasename);
        }
        if (extErrorBasenames != null) {
            serviceRouter.setExtErrorBasenames(extErrorBasenames);
        }
        if(securityManager == null){
        	securityManager = findOrCreateBean(SecurityManager.class, DefaultSecurityManager.class);
        }
        if(sessionManager == null){
        	sessionManager = findBean(SessionManager.class);
        }
        if(sessionManager != null){
        	securityManager.setSessionManager(sessionManager);
        	serviceRouter.setSessionManager(sessionManager);
        }
        if(appSecretManager == null){
        	appSecretManager = findOrCreateBean(AppSecretManager.class, FileBaseAppSecretManager.class);
        }
        securityManager.setAppSecretManager(appSecretManager);
        if(serviceAccessController == null){
        	serviceAccessController = findOrCreateBean(ServiceAccessController.class, DefaultServiceAccessController.class);
        }
        securityManager.setServiceAccessController(serviceAccessController);
        if(invokeTimesController == null){
        	invokeTimesController = findOrCreateBean(InvokeTimesController.class, DefaultInvokeTimesController.class);
        }
        serviceRouter.setInvokeTimesController(invokeTimesController);
        securityManager.setInvokeTimesController(invokeTimesController);
        securityManager.setFileUploadController(buildFileUploadController());
        serviceRouter.setSecurityManager(securityManager);
        serviceRouter.setThreadPoolExecutor(threadPoolExecutor);
        serviceRouter.setSignEnable(signEnable);
        serviceRouter.setServiceTimeoutSeconds(serviceTimeoutSeconds);
        FormattingConversionService conversionService = getFormattingConversionService();
        if(conversionService != null){
        	serviceRouter.setFormattingConversionService(conversionService);
        }
        serviceRouter.setThreadFerryClass(threadFerryClass);
        if(jsonMarshaller != null){
        	serviceRouter.setJsonMarshaller(jsonMarshaller);
        }
        if(xmlMarshaller != null){
        	serviceRouter.setXmlMarshaller(xmlMarshaller);
        }
        //注册拦截器
        ArrayList<Interceptor> interceptors = getInterceptors();
        if (interceptors != null) {
            for (Interceptor interceptor : interceptors) {
                serviceRouter.addInterceptor(interceptor);
            }
            if (logger.isInfoEnabled()) {
                logger.info("register total {} interceptors",interceptors.size());
            }
        }
        //注册监听器
        ArrayList<RopEventListener<RopEvent>> listeners = getListeners();
        if (listeners != null) {
            for (RopEventListener<RopEvent> listener : listeners) {
                serviceRouter.addListener(listener);
            }
            if (logger.isInfoEnabled()) {
                logger.info("register total {} listeners",listeners.size());
            }
        }
        //设置Spring上下文信息
        serviceRouter.setApplicationContext(this.applicationContext);
        //启动之
        serviceRouter.startup();
    }
	
	protected <T> T findOrCreateBean(Class<T> type, Class<? extends T> defaultType){
		T bean = findBean(type);
		if(bean != null){
			return bean;
		}
    	return BeanUtils.instantiate(defaultType == null ? type : defaultType);
	}
	
	protected <T> T findBean(Class<T> type){
		Map<String, T> map = applicationContext.getBeansOfType(type);
    	if(map != null && map.size() > 0){
    		for(T obj : map.values()){
    			return obj;
    		}
    	}
    	return null;
	}
    
    private FileUploadController buildFileUploadController() {
    	FileUploadController uploadController = findBean(FileUploadController.class);
    	if(uploadController != null){
    		return uploadController;
    	}
        Assert.notNull(this.uploadFileTypes, "Please set the updateFileTypes,if all,set *");
        if(ALL_FILE_TYPES.equals(uploadFileTypes.trim())){
            return new DefaultFileUploadController(this.uploadFileMaxSize);
        }else {
            String[] items = this.uploadFileTypes.split(",");
            List<String> fileTypes = Arrays.asList(items);
            return new DefaultFileUploadController(fileTypes, this.uploadFileMaxSize);
        }
    }
    
    protected FormattingConversionService getFormattingConversionService(){
    	if(formattingConversionService != null){
    		return formattingConversionService;
    	}
    	formattingConversionService = findBean(FormattingConversionService.class);
    	if(formattingConversionService != null){
    		return formattingConversionService;
    	}
    	return null;
    }

    private ArrayList<Interceptor> getInterceptors() {
        Map<String, InterceptorHolder> interceptorMap = this.applicationContext.getBeansOfType(InterceptorHolder.class);
        if (interceptorMap != null && interceptorMap.size() > 0) {
            ArrayList<Interceptor> interceptors = new ArrayList<Interceptor>(interceptorMap.size());

            //从Spring容器中获取Interceptor
            for (InterceptorHolder interceptorHolder : interceptorMap.values()) {
                interceptors.add(interceptorHolder.getInterceptor());
            }

            //根据getOrder()值排序
            Collections.sort(interceptors, new Comparator<Interceptor>() {
                public int compare(Interceptor o1, Interceptor o2) {
                    if (o1.getOrder() > o2.getOrder()) {
                        return 1;
                    } else if (o1.getOrder() < o2.getOrder()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            });
            return interceptors;
        } else {
            return null;
        }
    }

	private ArrayList<RopEventListener<RopEvent>> getListeners() {
        Map<String, RopEventListenerHodler> listenerMap = this.applicationContext.getBeansOfType(RopEventListenerHodler.class);
        if (listenerMap != null && listenerMap.size() > 0) {
            ArrayList<RopEventListener<RopEvent>> ropEventListeners = new ArrayList<RopEventListener<RopEvent>>(listenerMap.size());

            //从Spring容器中获取Interceptor
            for (RopEventListenerHodler listenerHolder : listenerMap.values()) {
                ropEventListeners.add(listenerHolder.getRopEventListener());
            }
            return ropEventListeners;
        } else {
            return null;
        }
    }

    public void setFormattingConversionService(FormattingConversionService formattingConversionService) {
        this.formattingConversionService = formattingConversionService;
    }

    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void setSignEnable(boolean signEnable) {
        this.signEnable = signEnable;
    }

    public void setExtErrorBasename(String extErrorBasename) {
        this.extErrorBasename = extErrorBasename;
    }

    public void setExtErrorBasenames(String[] extErrorBasenames) {
        this.extErrorBasenames = extErrorBasenames;
    }

    public void setServiceTimeoutSeconds(int serviceTimeoutSeconds) {
        this.serviceTimeoutSeconds = serviceTimeoutSeconds;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setAppSecretManager(AppSecretManager appSecretManager) {
        this.appSecretManager = appSecretManager;
    }

    public void setServiceAccessController(ServiceAccessController serviceAccessController) {
        this.serviceAccessController = serviceAccessController;
    }

    public String getUploadFileTypes() {
        return uploadFileTypes;
    }

    public void setUploadFileTypes(String uploadFileTypes) {
        this.uploadFileTypes = uploadFileTypes;
    }

    public int getUploadFileMaxSize() {
        return uploadFileMaxSize;
    }

    public void setUploadFileMaxSize(int uploadFileMaxSize) {
        this.uploadFileMaxSize = uploadFileMaxSize;
    }

	public void setXmlMarshaller(RopMarshaller xmlMarshaller) {
		this.xmlMarshaller = xmlMarshaller;
	}

	public void setJsonMarshaller(RopMarshaller jsonMarshaller) {
		this.jsonMarshaller = jsonMarshaller;
	}

	public void setServiceRouter(ServiceRouter serviceRouter) {
		this.serviceRouter = serviceRouter;
	}

	public void setSecurityManager(SecurityManager securityManager) {
		this.securityManager = securityManager;
	}
}

