package {{package}};

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.talend.sdk.component.api.input.Producer;{{#generic}}
import org.talend.sdk.component.api.processor.data.ObjectMap;{{/generic}}

import {{servicePackage}}.{{serviceName}};

public class {{className}} implements Serializable {
    private final {{configurationName}} configuration;
    private final {{serviceName}} service;

    public {{className}}(@Option("configuration") final {{configurationName}} configuration,
                         final {{serviceName}} service) {
        this.configuration = configuration;
        this.service = service;
    }

    @PostConstruct
    public void init() {
        // this method will be executed once for the whole component execution,
        // this is where you can establish a connection for instance
    }

    @Producer
    public {{#generic}}ObjectMap{{/generic}}{{^generic}}{{modelName}}{{/generic}} next() {
        // this is the method allowing you to go through the dataset associated
        // to the component configuration
        //
        // return null means the dataset has no more data to go through
        return null;
    }

    @PreDestroy
    public void release() {
        // this is the symmetric method of the init() one,
        // release potential connections you created or data you cached
    }
}
