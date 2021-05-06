/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2021.
 */
package dev.galasa.windows.spi;

import java.util.List;

import javax.validation.constraints.NotNull;

import dev.galasa.ManagerException;
import dev.galasa.framework.spi.InsufficientResourcesAvailableException;
import dev.galasa.framework.spi.ResourceUnavailableException;

public interface IWindowsProvisioner {

    IWindowsProvisionedImage provisionWindows(@NotNull String tag,
            @NotNull List<String> capabilities) throws ManagerException, ResourceUnavailableException, InsufficientResourcesAvailableException;

}
