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
package com.rop.security;

import com.rop.*;
import com.rop.annotation.HttpAction;
import com.rop.config.SystemParameterNames;
import com.rop.impl.DefaultServiceAccessController;
import com.rop.impl.SimpleRopRequestContext;
import com.rop.converter.UploadFileUtils;
import com.rop.response.MainError;
import com.rop.response.MainErrorType;
import com.rop.response.SubError;
import com.rop.response.SubErrorType;
import com.rop.session.SessionManager;
import com.rop.sign.DigestSignHandler;
import com.rop.sign.SignHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.*;

/**
 * @author 陈雄华
 * @version 1.0
 */
public class DefaultSecurityManager implements SecurityManager {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	protected ServiceAccessController serviceAccessController = new DefaultServiceAccessController();

	protected AppSecretManager appSecretManager = new FileBaseAppSecretManager();

	protected SessionManager sessionManager;

	protected InvokeTimesController invokeTimesController;

	protected FileUploadController fileUploadController;

	protected SignHandler signHandler;

	private static final Map<String, SubErrorType> INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS = new LinkedHashMap<String, SubErrorType>();

	static {
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("typeMismatch", SubErrorType.ISV_PARAMETERS_MISMATCH);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("NotNull", SubErrorType.ISV_MISSING_PARAMETER);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("NotEmpty", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Size", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Range", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Pattern", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Min", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Max", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("DecimalMin", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("DecimalMax", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Digits", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Past", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("Future", SubErrorType.ISV_INVALID_PARAMETE);
		INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.put("AssertFalse", SubErrorType.ISV_INVALID_PARAMETE);
	}

	public MainError validateSystemParameters(RopRequestContext context) {
		RopContext ropContext = context.getRopContext();
		MainError mainError = null;
		String method = context.getMethod();
		String version = context.getVersion();
		Locale locale = context.getLocale();
		// 1.检查appKey
		if (context.getAppKey() == null) {
			return MainErrors.getError(MainErrorType.MISSING_APP_KEY, locale, method,
					version, SystemParameterNames.getAppKey());
		}
		if (!appSecretManager.isValidAppKey(context.getAppKey())) {
			return MainErrors.getError(MainErrorType.INVALID_APP_KEY, locale, method,
					version, context.getAppKey());
		}
		// 2.检查会话
		mainError = checkSession(context);
		if (mainError != null) {
			return mainError;
		}
		// 3.检查method参数
		if (context.getMethod() == null) {
			return MainErrors.getError(MainErrorType.MISSING_METHOD, locale,
					SystemParameterNames.getMethod());
		} else {
			if (!ropContext.isValidMethod(context.getMethod())) {
				return MainErrors.getError(MainErrorType.INVALID_METHOD, locale, method);
			}
		}
		// 4.检查v参数
		if (context.getVersion() == null) {
			return MainErrors.getError(MainErrorType.MISSING_VERSION, locale, method,
					SystemParameterNames.getVersion());
		} else {
			if (!ropContext.isValidVersion(context.getMethod(), context.getVersion())) {
				return MainErrors.getError(MainErrorType.UNSUPPORTED_VERSION, locale, method, version);
			}
		}
		// 5.检查签名正确性
		mainError = checkSign(context);
		if (mainError != null) {
			return mainError;
		}
		// 6.检查服务方法的版本是否已经过期
		if (context.getServiceMethodDefinition().isObsoleted()) {
			return MainErrors.getError(MainErrorType.METHOD_OBSOLETED, locale, method, version);
		}
		// 7.检查请求HTTP方法的匹配性
		mainError = validateHttpAction(context);
		if (mainError != null) {
			return mainError;
		}
		// 8.检查 format
		if (!MessageFormat.isValidFormat(context.getFormat())) {
			return MainErrors.getError(MainErrorType.INVALID_FORMAT, locale, method, version, context.getFormat());
		}
		return null;
	}

	public MainError validateOther(RopRequestContext rrctx) {
		MainError mainError = null;
		// 1.判断应用/用户是否有权访问目标服务
		mainError = checkServiceAccessAllow(rrctx);
		if (mainError != null) {
			return mainError;
		}
		// 2.判断应用/会话/用户访问服务的次数或频度是否超限
		mainError = checkInvokeTimesLimit(rrctx);
		if (mainError != null) {
			return mainError;
		}
		// 3.如果是上传文件的服务，检查文件类型和大小是否满足要求
		mainError = checkUploadFile(rrctx);
		if (mainError != null) {
			return mainError;
		}
		// 4.检查业务参数合法性
		mainError = validateBusinessParams(rrctx);
		if (mainError != null) {
			return mainError;
		}
		return null;
	}

	private MainError checkUploadFile(RopRequestContext rrctx) {
		ServiceMethodHandler serviceMethodHandler = rrctx.getServiceMethodHandler();
		if (serviceMethodHandler == null || !serviceMethodHandler.hasUploadFiles()) {
			return null;
		}
		Locale locale = rrctx.getLocale();
		String method = rrctx.getMethod();
		String version = rrctx.getVersion();
		List<String> fileFieldNames = serviceMethodHandler.getUploadFileFieldNames();
		MainErrorType type = MainErrorType.UPLOAD_FAIL;
		for (String fileFieldName : fileFieldNames) {
			String paramValue = rrctx.getParamValue(fileFieldName);
			if (paramValue != null) {
				if (paramValue.indexOf("@") < 0) {
					String msg = "MESSAGE_VALID:not contain '@'.";
					return MainErrors.getError(type, locale, method, version, msg);
				} else {
					String fileType = UploadFileUtils.getFileType(paramValue);
					if (!fileUploadController.isAllowFileType(fileType)) {
						String msg = "FILE_TYPE_NOT_ALLOW:the valid file types is:"
								+ fileUploadController.getAllowFileTypes();
						return MainErrors.getError(type, locale, method, version, msg);
					}
					byte[] fileContent = UploadFileUtils.decode(paramValue);
					if (fileUploadController.isExceedMaxSize(fileContent.length)) {
						String msg = "EXCEED_MAX_SIZE:" + fileUploadController.getMaxSize() + "k";
						return MainErrors.getError(type, locale, method, version, msg);
					}
				}
			}
		}
		return null;
	}

	public void setInvokeTimesController(InvokeTimesController invokeTimesController) {
		this.invokeTimesController = invokeTimesController;
	}

	public void setServiceAccessController(ServiceAccessController serviceAccessController) {
		this.serviceAccessController = serviceAccessController;
	}

	public void setAppSecretManager(AppSecretManager appSecretManager) {
		this.appSecretManager = appSecretManager;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	public void setFileUploadController(FileUploadController fileUploadController) {
		this.fileUploadController = fileUploadController;
	}

	private MainError checkInvokeTimesLimit(RopRequestContext rrctx) {
		if (invokeTimesController.isAppInvokeFrequencyExceed(rrctx.getAppKey())) {
			return MainErrors.getError(MainErrorType.EXCEED_APP_INVOKE_FREQUENCY_LIMITED, rrctx.getLocale());
		} else if (invokeTimesController.isAppInvokeLimitExceed(rrctx.getAppKey())) {
			return MainErrors.getError(MainErrorType.EXCEED_APP_INVOKE_LIMITED, rrctx.getLocale());
		} else if (invokeTimesController.isSessionInvokeLimitExceed(rrctx.getAppKey(), rrctx.getSessionId())) {
			return MainErrors.getError(MainErrorType.EXCEED_SESSION_INVOKE_LIMITED, rrctx.getLocale());
		} else if (invokeTimesController.isUserInvokeLimitExceed(rrctx.getAppKey(), rrctx.getSession())) {
			return MainErrors.getError(MainErrorType.EXCEED_USER_INVOKE_LIMITED, rrctx.getLocale());
		} else {
			return null;
		}
	}

	/**
	 * 校验是否是合法的HTTP动作
	 *
	 * @param ropRequestContext
	 */
	private MainError validateHttpAction(RopRequestContext ropRequestContext) {
		MainError mainError = null;
		HttpAction[] httpActions = ropRequestContext.getServiceMethodDefinition().getHttpAction();
		if (httpActions.length > 0) {
			boolean isValid = false;
			HttpAction action = ropRequestContext.getHttpAction();
			for (HttpAction httpAction : httpActions) {
				if (httpAction == action) {
					isValid = true;
					break;
				}
			}
			if (!isValid) {
				Locale locale = ropRequestContext.getLocale();
				String method = ropRequestContext.getMethod();
				String version = ropRequestContext.getVersion();
				mainError = MainErrors.getError(MainErrorType.HTTP_ACTION_NOT_ALLOWED, 
					locale, method, version, action);
			}
		}
		return mainError;
	}

	public ServiceAccessController getServiceAccessController() {
		return serviceAccessController;
	}

	public AppSecretManager getAppSecretManager() {
		return appSecretManager;
	}

	private MainError checkServiceAccessAllow(RopRequestContext smc) {
		if (!getServiceAccessController().isAppGranted(smc.getAppKey(), smc.getMethod(), smc.getVersion())) {
			MainError mainError = SubErrors.getMainError(SubErrorType.ISV_INVALID_PERMISSION, smc.getLocale());
			SubError subError = SubErrors.getSubError(SubErrorType.ISV_INVALID_PERMISSION.value(),
					SubErrorType.ISV_INVALID_PERMISSION.value(), smc.getLocale());
			mainError.addSubError(subError);
			if (mainError != null && logger.isErrorEnabled()) {
				logger.debug("未向ISV开放该服务的执行权限(" + smc.getMethod() + ")");
			}
			return mainError;
		} else {
			if (!getServiceAccessController().isUserGranted(smc.getSession(), smc.getMethod(), smc.getVersion())) {
				MainError mainError = MainErrors.getError(MainErrorType.INSUFFICIENT_USER_PERMISSIONS, smc.getLocale(),
						smc.getMethod(), smc.getVersion());
				SubError subError = SubErrors.getSubError(SubErrorType.ISV_INVALID_PERMISSION.value(),
						SubErrorType.ISV_INVALID_PERMISSION.value(), smc.getLocale());
				mainError.addSubError(subError);
				if (mainError != null && logger.isErrorEnabled()) {
					logger.debug("未向会话用户开放该服务的执行权限(" + smc.getMethod() + ")");
				}
				return mainError;
			}
			return null;
		}
	}

	private MainError validateBusinessParams(RopRequestContext context) {
		@SuppressWarnings("unchecked")
		List<ObjectError> errorList = (List<ObjectError>) context
				.getAttribute(SimpleRopRequestContext.SPRING_VALIDATE_ERROR_ATTRNAME);

		// 将Bean数据绑定时产生的错误转换为Rop的错误
		if (errorList != null && errorList.size() > 0) {
			return toMainErrorOfSpringValidateErrors(errorList, context.getLocale(), context);
		} else {
			return null;
		}
	}

	/**
	 * 检查签名的有效性
	 *
	 * @param context
	 * @return
	 */
	private MainError checkSign(RopRequestContext context) {
		String method = context.getMethod();
		String version = context.getVersion();
		if (!context.isSignEnable() || context.getServiceMethodDefinition().isIgnoreSign()) {
			if (logger.isDebugEnabled()) {
				logger.warn("{}{}服务方法未开启签名", method, version);
			}
			return null;
		}
		Locale locale = context.getLocale();
		String signKey = SystemParameterNames.getSign();
		// 系统级签名开启,且服务方法需求签名
		if (context.getSign() == null) {
			return MainErrors.getError(MainErrorType.MISSING_SIGNATURE, locale, method, version, signKey);
		} else {
			// 获取需要签名的参数
			List<String> ignoreSignFieldNames = context.getServiceMethodHandler().getIgnoreSignFieldNames();
			List<String> ignoreSigns = SystemParameterNames.getIgnoreSignFieldNames();
			if (ignoreSigns != null && ignoreSigns.size() > 0) {
				ignoreSignFieldNames.addAll(ignoreSigns);
			}
			if (!getSignHandler().signCheck(context.getSign(), context.getAllParams(), ignoreSignFieldNames)) {
				if (logger.isErrorEnabled()) {
					logger.error(context.getAppKey() + "的签名不合法，请检查");
				}
				return MainErrors.getError(MainErrorType.INVALID_SIGNATURE, locale, method, version);
			} else {
				return null;
			}
		}
	}

	/**
	 * 是否是合法的会话
	 *
	 * @param context
	 * @return
	 */
	private MainError checkSession(RopRequestContext context) {
		// 不需要进行session检查
		if (context.getServiceMethodHandler() == null
				|| !context.getServiceMethodHandler().getServiceMethodDefinition().isNeedInSession()) {
			return null;
		}
		// 需要进行session检查
		if (context.getSessionId() == null) {
			return MainErrors.getError(MainErrorType.MISSING_SESSION, context.getLocale(), context.getMethod(),
					context.getVersion(), SystemParameterNames.getSessionId());
		} else {
			if (!isValidSession(context)) {
				return MainErrors.getError(MainErrorType.INVALID_SESSION, context.getLocale(), context.getMethod(),
						context.getVersion(), context.getSessionId());
			}
		}
		return null;
	}

	private boolean isValidSession(RopRequestContext smc) {
		if (sessionManager.getSession(smc.getSessionId()) == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(smc.getSessionId() + "会话不存在，请检查。");
			}
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 将通过JSR 303框架校验的错误转换为Rop的错误体系
	 *
	 * @param allErrors
	 * @param locale
	 * @return
	 */
	private MainError toMainErrorOfSpringValidateErrors(List<ObjectError> allErrors, Locale locale,
			RopRequestContext context) {
		if (hastSubErrorType(allErrors, SubErrorType.ISV_MISSING_PARAMETER)) {
			return getBusinessParameterMainError(allErrors, locale, SubErrorType.ISV_MISSING_PARAMETER, context);
		} else if (hastSubErrorType(allErrors, SubErrorType.ISV_PARAMETERS_MISMATCH)) {
			return getBusinessParameterMainError(allErrors, locale, SubErrorType.ISV_PARAMETERS_MISMATCH, context);
		} else {
			return getBusinessParameterMainError(allErrors, locale, SubErrorType.ISV_INVALID_PARAMETE, context);
		}
	}

	/**
	 * 判断错误列表中是否包括指定的子错误
	 *
	 * @param allErrors
	 * @param subErrorType1
	 * @return
	 */
	private boolean hastSubErrorType(List<ObjectError> allErrors, SubErrorType subErrorType1) {
		for (ObjectError objectError : allErrors) {
			if (objectError instanceof FieldError) {
				FieldError fieldError = (FieldError) objectError;
				if (INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.containsKey(fieldError.getCode())) {
					SubErrorType tempSubErrorType = INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.get(fieldError.getCode());
					if (tempSubErrorType == subErrorType1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * 生成对应子错误的错误类
	 *
	 * @param allErrors
	 * @param locale
	 * @param subErrorType
	 * @return
	 */
	private MainError getBusinessParameterMainError(List<ObjectError> allErrors, Locale locale,
			SubErrorType subErrorType, RopRequestContext context) {
		MainError mainError = SubErrors.getMainError(subErrorType, locale, context.getMethod(), context.getVersion());
		for (ObjectError objectError : allErrors) {
			if (objectError instanceof FieldError) {
				FieldError fieldError = (FieldError) objectError;
				SubErrorType tempSubErrorType = INVALIDE_CONSTRAINT_SUBERROR_MAPPINGS.get(fieldError.getCode());
				if (tempSubErrorType == subErrorType) {
					String subErrorCode = SubErrors.getSubErrorCode(tempSubErrorType, fieldError.getField(),
							fieldError.getRejectedValue());
					SubError subError = SubErrors.getSubError(subErrorCode, tempSubErrorType.value(), locale,
							fieldError.getField(), fieldError.getRejectedValue());
					mainError.addSubError(subError);
				}
			}
		}
		return mainError;
	}

	public void setSignHandler(SignHandler signHandler) {
		this.signHandler = signHandler;
	}

	public SignHandler getSignHandler() {
		if(signHandler == null){
			signHandler = new DigestSignHandler("SHA-1", appSecretManager);
		}
		return signHandler;
	}
}
