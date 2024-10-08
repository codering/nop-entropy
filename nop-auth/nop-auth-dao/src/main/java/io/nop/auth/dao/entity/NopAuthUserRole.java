/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen.NopAuthUserRolePkBuilder;
import io.nop.auth.dao.entity._gen._NopAuthUserRole;

@BizObjName("NopAuthUserRole")
public class NopAuthUserRole extends _NopAuthUserRole {
    public NopAuthUserRole() {
    }

    public static NopAuthUserRolePkBuilder newPk() {
        return new NopAuthUserRolePkBuilder();
    }

}
