package gate.creole.measurements;

import gate.creole.PackagedController;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.AutoInstanceParam;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;

@CreoleResource(name = "Measurements", icon = "measurements", autoinstances = @AutoInstance(parameters = {
	@AutoInstanceParam(name="pipelineURL", value="resources/measurements.xgapp"),
	@AutoInstanceParam(name="menu", value="Measurements")}))
public class MeasurementsApplication extends PackagedController {

}
