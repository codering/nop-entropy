package io.nop.core.resource.tenant;

import io.nop.core.resource.ResourceHelper;

import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_DISABLED_PATHS;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED_PATHS;

class ConfigurableResourceTenantChecker implements IResourceTenantChecker {
    private final Set<String> enabledPaths;
    private final Set<String> disabledPaths;

    public ConfigurableResourceTenantChecker(Set<String> enabledPaths, Set<String> disabledPaths) {
        this.enabledPaths = enabledPaths;
        this.disabledPaths = disabledPaths;
    }

    public static ConfigurableResourceTenantChecker createFromConfig() {
        Set<String> enabledPaths = CFG_TENANT_RESOURCE_ENABLED_PATHS.get();
        Set<String> disabledPaths = CFG_TENANT_RESOURCE_DISABLED_PATHS.get();
        if (enabledPaths == null && disabledPaths == null)
            return null;
        return new ConfigurableResourceTenantChecker(enabledPaths, disabledPaths);
    }

    @Override
    public boolean isEnableTenant() {
        return true;
    }

    @Override
    public boolean isSupportTenant(String resourcePath) {
        String stdPath = ResourceHelper.getStdPath(resourcePath);

        if (enabledPaths != null) {
            for (String path : enabledPaths) {
                if (stdPath.startsWith(path))
                    return true;
            }
        }

        if (disabledPaths != null) {
            for (String path : disabledPaths) {
                if (stdPath.startsWith(path))
                    return false;
            }
        }

        return true;
    }
}
