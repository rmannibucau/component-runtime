package {{package}};

{{#generic}}
import static java.util.Collections.emptySet;

import org.talend.sdk.component.api.processor.data.ObjectMap;
{{/generic}}
// this is the pojo which will be used to represent your data
public class {{className}} {{#generic}}implements ObjectMap {{/generic}}{
    {{#generic}}
    @Override
    public Object get(final String location) {
        return null;
    }

    @Override
    public ObjectMap getMap(final String location) {
        return null;
    }

    @Override
    public Collection<ObjectMap> getCollection(final String location) {
        return null;
    }

    @Override
    public synchronized Set<String> keys() {
        return emptySet();
    }
    {{/generic}}{{^generic}}{{#structure}}
    private {{type}} {{name}};

    public {{type}} get{{methodName}}() {
        return {{name}};
    }

    public void set{{methodName}}(final {{type}} {{name}}) {
        this.{{name}} = {{name}};
    }
    {{/structure}}{{/generic}}
}
