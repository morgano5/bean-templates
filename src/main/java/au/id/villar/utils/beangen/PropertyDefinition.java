package au.id.villar.utils.beangen;

public class PropertyDefinition {
    private final String name;
    private final String type;
    private boolean isFinal = false;
    private boolean needsGetter = true;
    private boolean needsSetter = true;

    public PropertyDefinition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public boolean needsGetter() {
        return needsGetter;
    }

    public void setNeedsGetter(boolean needsGetter) {
        this.needsGetter = needsGetter;
    }

    public boolean needsSetter() {
        return needsSetter;
    }

    public void setNeedsSetter(boolean needsSetter) {
        this.needsSetter = needsSetter;
    }

    public VariableDefinition asVariableDefinition() {
        return new VariableDefinition(name, type);
    }
}
