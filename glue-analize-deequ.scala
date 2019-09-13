import com.amazonaws.services.glue.util.JsonOptions
import com.amazonaws.services.glue.{DynamicFrame, GlueContext}
import org.apache.spark.SparkContext


import com.amazon.deequ.analyzers.runners.{AnalysisRunner, AnalyzerContext}
import com.amazon.deequ.analyzers.runners.AnalyzerContext.successMetricsAsDataFrame
import com.amazon.deequ.analyzers.{Compliance, Correlation, Size, Completeness, Mean, ApproxCountDistinct}


object GlueApp{
    
   def main(sysArgs: Array[String]) = {
       
        val sc: SparkContext = new SparkContext()
        val glueContext: GlueContext = new GlueContext(sc)
        val sparkSession = glueContext.getSparkSession
        
        val ouputDeequDir = "s3://bucket-glue-target/target/deequ/productos_analysis"
        
        val dataset = sparkSession.read.parquet("s3://bucket-glue-target/target/productos")

        val analysisResult: AnalyzerContext = { AnalysisRunner
          // data to run the analysis on
          .onData(dataset)
          // define analyzers that compute metrics
          .addAnalyzer(Size())
          .addAnalyzer(Completeness("review_id"))
          .addAnalyzer(ApproxCountDistinct("review_id"))
          .addAnalyzer(Mean("star_rating"))
          .addAnalyzer(Compliance("top star_rating", "star_rating >= 4.0"))
          .addAnalyzer(Correlation("total_votes", "star_rating"))
          .addAnalyzer(Correlation("total_votes", "helpful_votes"))
          // compute metrics
          .run()
        }

        // convert check results to a Spark data frame
        val resultDataFrame = successMetricsAsDataFrame(sparkSession, analysisResult)
        // convert check results to a Dynamic Frame
        val resultDynamicDataFrame = DynamicFrame(resultDataFrame, glueContext)
        // Enviar resultados a S3
        glueContext.getSinkWithFormat(connectionType = "s3", options = JsonOptions(Map("path" -> ouputDeequDir)),
            format = "parquet", transformationContext = "").writeDynamicFrame(resultDynamicDataFrame)
   }
}