/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.http.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;

import dev.galasa.ManagerException;
import dev.galasa.framework.spi.AbstractGherkinManager;
import dev.galasa.framework.spi.AbstractManager;
import dev.galasa.framework.spi.AnnotatedField;
import dev.galasa.framework.spi.GenerateAnnotatedField;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IGherkinExecutable;
import dev.galasa.framework.spi.IGherkinManager;
import dev.galasa.framework.spi.IManager;
import dev.galasa.framework.spi.ResourceUnavailableException;
import dev.galasa.framework.spi.language.GalasaTest;
import dev.galasa.http.HttpClient;
import dev.galasa.http.IHttpClient;
import dev.galasa.http.internal.gherkin.GherkinPostText;
import dev.galasa.http.internal.gherkin.GherkinStatements;
import dev.galasa.http.spi.IHttpManagerSpi;

@Component(service = { IManager.class, IGherkinManager.class })
public class HttpManagerImpl extends AbstractGherkinManager implements IHttpManagerSpi {

    private static final Log  logger              = LogFactory.getLog(HttpManagerImpl.class);
    private List<IHttpClient> instantiatedClients = new ArrayList<>();

    @GenerateAnnotatedField(annotation = HttpClient.class)
    public IHttpClient generateHttpClient(Field field, List<Annotation> annotations) {
        return newHttpClient();
    }

    @Override
    public void provisionGenerate() throws ManagerException, ResourceUnavailableException {
        generateAnnotatedFields(HttpManagerField.class);
    }

    @Override
    public void shutdown() {
        for (IHttpClient client : instantiatedClients) {
            client.close();
        }
    }

    @Override
    public void initialise(@NotNull IFramework framework, @NotNull List<IManager> allManagers,
            @NotNull List<IManager> activeManagers, @NotNull GalasaTest galasaTest) throws ManagerException {
        super.initialise(framework, allManagers, activeManagers, galasaTest);

        if(galasaTest.isJava()) {
            List<AnnotatedField> ourFields = findAnnotatedFields(HttpManagerField.class);
            if (!ourFields.isEmpty()) {
                youAreRequired(allManagers, activeManagers);
            }
        } else if(galasaTest.isGherkin()) {
            GherkinStatements.register(galasaTest, this, allManagers, activeManagers);
        }
    }

    @Override
    public void executeGherkin(@NotNull IGherkinExecutable executable, Map<String, Object> testVariables)
            throws ManagerException {
        try {
            switch(executable.getKeyword()) {
                case WHEN:
                    Matcher matcherPostText = GherkinPostText.pattern.matcher(executable.getValue());
                    if(matcherPostText.matches()) {
                        GherkinPostText.execute(matcherPostText, this, testVariables);
                        return;
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            throw new ManagerException("Unable to execute statement: " + executable.getValue(), e);
        }
    }

    @Override
    public void youAreRequired(@NotNull List<IManager> allManagers, @NotNull List<IManager> activeManagers)
            throws ManagerException {
        if (activeManagers.contains(this)) {
            return;
        }

        activeManagers.add(this);
    }

    @Override
    public @NotNull IHttpClient newHttpClient() {
        IHttpClient client = new HttpClientImpl(180000, logger);
        instantiatedClients.add(client);
        return client;
    }
    
    @Override
    public @NotNull IHttpClient newHttpClient(int timeout) {
        IHttpClient client = new HttpClientImpl(timeout, logger);
        instantiatedClients.add(client);
        return client;
    }
    
    @Override
    public boolean doYouSupportSharedEnvironments() {
        return true;   // this manager does not provision resources, therefore support environments 
    }

}
