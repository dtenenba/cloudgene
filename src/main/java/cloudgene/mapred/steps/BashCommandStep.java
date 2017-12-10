package cloudgene.mapred.steps;

import genepi.hadoop.HdfsUtil;
import genepi.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import cloudgene.mapred.jobs.CloudgeneContext;
import cloudgene.mapred.jobs.CloudgeneStep;
import cloudgene.mapred.jobs.Message;
import cloudgene.mapred.wdl.WdlStep;

public class BashCommandStep extends CloudgeneStep {

	@Override
	public boolean run(WdlStep step, CloudgeneContext context) {

		String cmd = step.get("exec");
		if (cmd == null) {
			cmd = step.get("cmd");
		}

		if (cmd == null) {
			context.error("No 'exec' or 'cmd' parameter found.");
		}

		if (cmd.isEmpty()) {
			context.error("'exec' or 'cmd' parameter cannot be an empty string.");
		}

		String[] params = cmd.split(" ");

		File file = new File(params[0]);

		if (!file.exists()) {
			context.error("Command '" + file.getAbsolutePath()
					+ "' was not found. Please set the correct path in the cloudgene.yaml file.");
			return false;
		}

		if (!file.canExecute()) {
			context.error("Command '" + file.getAbsolutePath()
					+ "' was found but can not be executed. Please check the permissions.");
			return false;
		}

		List<String> command = new Vector<String>();
		for (String param : params) {

			// checkout hdfs file
			if (param.startsWith("hdfs://")) {
				String name = FileUtil.getFilename(param);
				String localFile = FileUtil.path(((CloudgeneContext) context).getLocalTemp(), "local_" + name);
				try {
					HdfsUtil.checkOut(param, localFile);
					command.add(new File(localFile).getAbsolutePath());
				} catch (IOException e) {
					context.log(e.getMessage());
					command.add(param);
				}

			} else {

				command.add(param);

			}

		}

		try {
			context.beginTask("Running Command...");
			boolean successful = executeCommand(command, context);
			if (successful) {
				context.endTask("Execution successful.", Message.OK);
				return true;
			} else {
				context.endTask("Execution failed. Please have a look at the logfile for details.", Message.ERROR);
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

}
