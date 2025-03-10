/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model;

import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model._gen._RuleOutputDefineModel;

public class RuleOutputDefineModel extends _RuleOutputDefineModel {
    public RuleOutputDefineModel() {

    }

    public String getWeightName() {
        return getName() + RuleConstants.WEIGHT_NAME_POSTFIX;
    }
}
