package org.dynmap;

public class TestComponent extends Component {

    public TestComponent(DynmapCore plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        Log.info("Hello! I'm a component that does stuff! Like saying what is in my configuration: " + configuration.getString("stuff"));
    }
    
}
