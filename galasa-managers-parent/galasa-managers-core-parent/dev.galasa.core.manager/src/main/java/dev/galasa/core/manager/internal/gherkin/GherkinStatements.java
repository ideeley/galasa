package dev.galasa.core.manager.internal.gherkin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.galasa.ManagerException;
import dev.galasa.core.manager.internal.CoreManager;
import dev.galasa.framework.TestRunException;
import dev.galasa.framework.spi.IGherkinExecutable;
import dev.galasa.framework.spi.IGherkinManager;
import dev.galasa.framework.spi.IManager;
import dev.galasa.framework.spi.language.GalasaTest;

public class GherkinStatements {

    public static void register(GalasaTest galasaTest, CoreManager manager, List<IManager> allManagers, List<IManager> activeManagers) throws ManagerException {
		for(IGherkinExecutable gherkinExecutable : galasaTest.getGherkinTest().getAllExecutables()) {
            switch (gherkinExecutable.getKeyword()) {
                case GIVEN:
                    match(GherkinStoreVariable.pattern, gherkinExecutable, manager, allManagers, activeManagers, GherkinStoreVariable.dependencies);
                    break;

                case THEN:
                    match(GherkinLog.pattern, gherkinExecutable, manager, allManagers, activeManagers, GherkinLog.dependencies);
                    break;
            
                default:
                    break;
            }
		}
    }
    
    private static void match(Pattern regexPattern, IGherkinExecutable gherkinExecutable, CoreManager manager,
            List<IManager> allManagers, List<IManager> activeManagers, Class<?>[] dependencies) throws ManagerException {
        Matcher gherkinMatcher = regexPattern.matcher(gherkinExecutable.getValue());
        if(gherkinMatcher.matches()) {
            try {
                manager.youAreRequired(allManagers, activeManagers);
                gherkinExecutable.registerManager((IGherkinManager) manager);
                for(Class<?> dependencyManager : dependencies) {
                    for (IManager otherManager : allManagers) {
                        if (dependencyManager.isAssignableFrom(otherManager.getClass())) {
                            otherManager.youAreRequired(allManagers, activeManagers);
                        }
                    }
                }
            } catch (TestRunException e) {
                throw new ManagerException("Unable to register Manager for Gherkin Statement", e);
            }
        }
    }
    
}