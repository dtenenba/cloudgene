package cloudgene.mapred.jobs.engine.graph;

import genepi.hadoop.common.WorkflowStep;
import genepi.io.FileUtil;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cloudgene.mapred.jobs.AbstractJob;
import cloudgene.mapred.jobs.CloudgeneContext;
import cloudgene.mapred.jobs.CloudgeneJob;
import cloudgene.mapred.jobs.CloudgeneStep;
import cloudgene.mapred.steps.ErrorStep;
import cloudgene.mapred.steps.ExternStep;
import cloudgene.mapred.wdl.WdlStep;

public class GraphNode implements Runnable {
	private WdlStep step;

	private CloudgeneContext context;

	private AbstractJob job;

	private CloudgeneStep instance;

	private static final Log log = LogFactory.getLog(CloudgeneJob.class);

	private boolean successful = false;

	private boolean finish = false;

	private List<String> inputs;

	private List<String> outputs;

	private long time;

	public GraphNode(WdlStep step, CloudgeneContext context)
			throws MalformedURLException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		this.step = step;
		this.context = context;
		this.job = context.getJob();
		inputs = new Vector<String>();
		outputs = new Vector<String>();
		instance();
	}

	public WdlStep getStep() {
		return step;
	}

	public void setStep(WdlStep step) {
		this.step = step;
	}

	private void instance() throws MalformedURLException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {

		if (step.getPig() != null) {

			// pig script
			step.setClassname("cloudgene.mapred.steps.PigHadoop");

		}
		if (step.getSpark() != null) {

			// spark
			step.setClassname("cloudgene.mapred.steps.SparkStep");

		}else if (step.getRmd() != null) {

			// rscript
			step.setClassname("cloudgene.mapred.steps.RMarkdown");

		} else if (step.getClassname() != null) {

			// custom class

		} else if (step.getExec() != null) {

			// command
			step.setClassname("cloudgene.mapred.steps.Command");

		} else {

			// mapreduce
			step.setClassname("cloudgene.mapred.steps.MapReduce");

		}

		// create instance

		String path = new File(context.getWorkingDirectory()).getAbsolutePath();
		final String jar = FileUtil.path(path, step.getJar());

		try {

			File file = new File(jar);

			if (file.exists()) {

				URL url = file.toURL();

				URLClassLoader urlCl = new URLClassLoader(new URL[] { url },
						CloudgeneJob.class.getClassLoader());
				Class myClass = urlCl.loadClass(step.getClassname());

				Object object = myClass.newInstance();

				if (object instanceof CloudgeneStep) {
					instance = (CloudgeneStep) object;
				} else if (object instanceof WorkflowStep) {
					instance = new ExternStep((WorkflowStep) object);
				} else {
					instance = new ErrorStep(
							"Error during initialization: class "
									+ step.getClassname() + " ( "  + object.getClass().getSuperclass().getCanonicalName() + ") "
									+ " has to extend CloudgeneStep or WorkflowStep. ");

				}

			} else {

				instance = new ErrorStep(
						"Error during initialization: Jar file '" + jar
								+ "' not found.");

			}

		} catch (Exception e) {
			Writer writer = new StringWriter();
			PrintWriter printWriter = new PrintWriter(writer);
			e.printStackTrace(printWriter);
			String s = writer.toString();
			instance = new ErrorStep("Error during initialization: " + s);

		}

		instance.setName(step.getName());
		instance.setJob(job);
	}

	@Override
	public void run() {

		// JobDao dao = new JobDao();
		context.setCurrentStep(instance);
		job.getSteps().add(instance);
		// dao.update(job);

		job.writeOutputln("------------------------------------------------------");
		job.writeOutputln(step.getName());
		job.writeOutputln("------------------------------------------------------");

		long start = System.currentTimeMillis();

		try {

			// if (step.isCache()) {
			// job.writeOutputln("cache = true");
			// instance = new CachedCloudgeneStep(instance);
			// } else {
			// job.writeOutputln("cache = false");
			// }

			instance.setup(context);
			boolean successful = instance.run(step, context);

			if (!successful) {
				job.writeLog("  " + step.getName() + " [ERROR]");
				successful = false;
				finish = true;
				return;
			} else {
				long end = System.currentTimeMillis();
				long time = end - start;

				long h = (long) (Math.floor((time / 1000) / 60 / 60));
				long m = (long) ((Math.floor((time / 1000) / 60)) % 60);

				String t = (h > 0 ? h + " h " : "")
						+ (m > 0 ? m + " min " : "")
						+ (int) ((Math.floor(time / 1000)) % 60) + " sec";

				job.writeLog("  " + step.getName() + " [" + t + "]");
				setTime(time);
			}
		} catch (Exception e) {
			log.error("Running extern job failed!", e);
			successful = false;
			finish = true;
			return;
		}

		finish = true;
		successful = true;

	}

	public boolean isSuccessful() {
		return successful;
	}

	public void kill() {
		if (instance != null) {
			instance.kill();
		}
	}

	public void updateProgress() {
		if (instance != null) {
			instance.updateProgress();
		}
	}

	public int getMapProgress() {
		if (instance != null) {
			return instance.getMapProgress();
		} else {
			return 0;
		}
	}

	public int getReduceProgress() {
		if (instance != null) {
			return instance.getReduceProgress();
		} else {
			return 0;
		}
	}

	public boolean isFinish() {
		return finish;
	}

	public void addInput(String input) {
		inputs.add(input);
	}

	public void addOutput(String output) {
		outputs.add(output);
	}

	public List<String> getInputs() {
		return inputs;
	}

	public List<String> getOutputs() {
		return outputs;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public long getExecutionTime() {
		return time;
	}

}
