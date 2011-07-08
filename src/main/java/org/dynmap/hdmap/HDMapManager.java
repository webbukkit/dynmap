package org.dynmap.hdmap;

import java.util.HashMap;

import org.dynmap.ConfigurationNode;
import org.dynmap.Log;

public class HDMapManager {
    public HashMap<String, HDShader> shaders = new HashMap<String, HDShader>();
    public HashMap<String, HDPerspective> perspectives = new HashMap<String, HDPerspective>();
    
    public void loadHDShaders(ConfigurationNode shadercfg) {
        Log.verboseinfo("Loading shaders...");
        for(HDShader shader : shadercfg.<HDShader>createInstances("shaders", new Class<?>[0], new Object[0])) {
            if(shaders.containsKey(shader.getName())) {
                Log.severe("Duplicate shader name '" + shader.getName() + "' - shader ignored");
            }
            shaders.put(shader.getName(), shader);
        }
        Log.info("Loaded " + shaders.size() + " shaders.");
    }

    public void loadHDPerspectives(ConfigurationNode perspectivecfg) {
        Log.verboseinfo("Loading perspectives...");
        for(HDPerspective perspective : perspectivecfg.<HDPerspective>createInstances("perspectives", new Class<?>[0], new Object[0])) {
            if(perspectives.containsKey(perspective.getName())) {
                Log.severe("Duplicate perspective name '" + perspective.getName() + "' - perspective ignored");
            }
            perspectives.put(perspective.getName(), perspective);
        }
        Log.info("Loaded " + perspectives.size() + " perspectives.");
    }
}
