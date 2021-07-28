package net.enilink.platform.ldp.ldPatch.parse;

import net.enilink.komma.parser.sparql.tree.GraphNode;

public class Bind implements Operation {
    private String name;
    private GraphNode value ;
    private Path path;

    public Bind(String name, GraphNode value, Path path){
        this.name = name;
        this.path = path;
        this. value = value;
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public GraphNode getValue() {
        return value;
    }
}
