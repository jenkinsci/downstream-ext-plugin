package hudson.plugins.downstream_ext;

public enum MatrixTrigger {
	ONLY_PARENT("Trigger only the parent job"),
	ONLY_CONFIGURATIONS("Trigger for each configuration"),
	BOTH("Trigger for parent and each configuration");
	
	private final String description;

	private MatrixTrigger(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
