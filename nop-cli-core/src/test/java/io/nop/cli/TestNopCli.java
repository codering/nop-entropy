/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestNopCli {

    @Inject
    CommandLine.IFactory factory;

    @Test
    public void testGen() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CODEGEN_TRACE_ENABLED, true);
        String[] args = new String[]{"gen", "src/test/resources/io/nop/cli/test.orm.xlsx",
                "-t=v:/nop/templates/orm",
                "-o", "target/gen", "-F"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenOrmExcelForPdm() {
        String[] args = new String[]{"gen-orm-excel", "src/test/resources/io/nop/cli/test.pdm",
                "-o", "target/gen/gen-from-pdm.orm.xlsx",};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenOrmExcelForPdman() {
        String[] args = new String[]{"gen-orm-excel", "src/test/resources/io/nop/cli/test.pdma.json",
                "-o", "target/gen/gen-from-pdman.orm.xlsx",};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenXpt() {
        String[] args = new String[]{"gen-file", "src/test/resources/data/data.json5",
                "-t", "/xpt/test.xpt.xlsx",
                "-o", "target/gen/test.xpt.html"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testRunTask(){
        CoreInitialization.destroy();
        String[] args = new String[]{"run-task", "src/test/resources/task/test.task.xml",
                "-if", "src/test/resources/data/data.json5"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }
}
