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

import com.rop.session.Session;

/**
 * <pre>
 *    安全控制控制器，决定用户是否有。
 * </pre>
 *
 * @author 陈雄华
 * @version 1.0
 */
public interface ServiceAccessController {

    /**
     * 服务方法是否向ISV开放
     * @param method
     * @param userId
     * @return {@link Boolean}
     */
    boolean isAppGranted(String appKey, String method, String version);

    /**
     *  服务方法是否向当前用户开放
     * @param ropRequestContext
     * @return {@link Boolean}
     */
    boolean isUserGranted(Session session,String method,String version);
}

