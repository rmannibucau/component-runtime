package {{package}};

import static java.util.Collections.singletonList;

import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Assessor;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionSize;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.input.Split;

import {{servicePackage}}.{{serviceName}};

//
// this class role is to enable the work to be distributed in environments supporting it.
//
@Version(1) // default version is 1, if some configuration changes happen between 2 versions you can add a migrationHandler
@Icon({{icon}}) // you can use a custom one using @Icon(value=CUSTOM, custom="filename") and adding icons/filename_icon32.png in resources
@PartitionMapper(name = "{{name}}"{{#infinite}}, infinite = true{{/infinite}})
public class {{className}} implements Serializable {
    private final {{configurationName}} configuration;
    private final {{serviceName}} service;

    public {{className}}(@Option("configuration") final {{configurationName}} configuration,
                         final {{serviceName}} service) {
        this.configuration = configuration;
        this.service = service;
    }

    @Assessor
    public long estimateSize() throws SQLException {
        // this method should return the estimation of the dataset size
        // it is recommanded to return a byte value
        // if you don't have the exact size you can use a rough estimation
        return 1L;
    }

    @Split
    public List<{{className}}> split(@PartitionSize final long bundles) throws SQLException {
        // overall idea here is to split the work related to configuration in bundles of size "bundles"
        //
        // for instance if your estimateSize() returned 1000 and you can run on 10 nodes
        // then the environment can decide to run it concurrently (10 * 100).
        // In this case bundles = 100 and we must try to return 10 {{className}} with 1/10 of the overall work each.
        //
        // default implementation returns this which means it doesn't support the work to be split
        return singletonList(this);
    }

    @Emitter
    public {{sourceName}} createWorker() {
        // here we create an actual worker,
        // you are free to rework the configuration etc but our default generated implementation
        // propagates the partition mapper entries.
        return new {{sourceName}}(configuration, service);
    }
}
