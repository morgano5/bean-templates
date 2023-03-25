package au.id.villar.utils.beangen;

import java.util.List;

record ConstructorDefinition(String accessModifier, List<VariableDefinition> parameters, boolean usedByBuilder) {
}
