/*
 * Copyright 2012-2017 the original author or authors.
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
package com.rop.client;

import com.rop.converter.RopConverter;
import com.rop.sign.SignHandler;

/**
 * <pre>
 * 功能说明：
 * </pre>
 *
 * @author 陈雄华
 * @version 1.0
 */
public interface RopClient {

    /**
     * 添加自定义的转换器
     *
     * @param ropConverter
     */
    void addRopConvertor(RopConverter<String, ?> ropConverter);

    /**
     * 设置method系统参数的参数名，下同
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setAppKeyParamName(String paramName);

    /**
     * 设置sessionId的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setSessionIdParamName(String paramName);

    /**
     * 设置method的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setMethodParamName(String paramName);

    /**
     * 设置version的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setVersionParamName(String paramName);

    /**
     * 设置format的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setFormatParamName(String paramName);

    /**
     * 设置locale的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setLocaleParamName(String paramName);

    /**
     * 设置sign的参数名
     *
     * @param paramName
     * @return RopClient
     */
    RopClient setSignParamName(String paramName);

    /**
     * 设置sessionId
     *
     * @param sessionId
     */
    void setSessionId(String sessionId);
    
    /**
     * 设置签名处理接口对象
     * @param handler
     * @return RopClient
     */
    RopClient setSignHandler(SignHandler handler);

    /**
     * 创建一个新的服务请求
     * @return
     */
    ClientRequest buildClientRequest();
}

