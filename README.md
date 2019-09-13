# Workshop

En este workshop vamos a utilizar varias herramientas y servicios de AWS, entre ellos, S3, Glue ETL, Glue Data Catalog, Quicksight Deeque para hacer un proceso de ETL y Data Quality.

Crear una cuenta nueva de AWS o conectarse a una ya existente.

Vamos a necesitar crear una política y rol usuando IAM, para ello:

1. Crear rol para usar en Glue

    * Ir al servicio **IAM**
    * Ir al apartado **Roles**
    * **Create role**
    * Seleccionar **Glue** como servicio que utilizará el rol -> **Next**
    * **Create Policy**
    * Seleccionar JSON
    * Copiar JSON
    ```json
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Sid": "VisualEditor0",
                "Effect": "Allow",
                "Action": [
                    "logs:CreateLogStream",
                    "s3:*",
                    "logs:CreateLogGroup",
                    "logs:PutLogEvents",
                    "glue:*"
                ],
                "Resource": "*"
            }
        ]
    }
    ```
    * **Review Policy**
    * Dar nombre *"my-glue-policy"*
    * **Create policy**
    * Volver a la pestaña del rol en el navegador y dar click al ícono de **refresh**
    * Seleccionar policy creada
    * **Next: Tags** -> **Next: Review**
    * Dar nombre *"my-glue-role"*
    * **Create role**

2. Lanzar crawler sobre source S3 externo (Glue Data Catalog)

    * Ir al servicio **AWS Glue**
    * Ir al apartado **Crawlers**
    * Click en **Add crawler**
    * Nombre crawler *"my-crawler-source"* -> **Next**
    * Data stores -> Next
    * Elegir **S3** y seleccionar *Specified path in another account*, ingresar *"s3://amazon-reviews-pds/parquet/product_category=Electronics"*
    * **Add another store "No"** -> **Next**
    * **Choose an existing IAM role**, seleccione el rol creado -> **Next**
    * Frequency **Run on Demand** -> **Next**
    * Add database -> **Database name** *"db-source"* -> **Create** -> **Next**
    * **Finish**
    * Click en **Run it now**

    Al lanzar el crawler lo que este hace es analizar la data del *Data store* seleccionado y deducir información referente a las columnas existentes, tipos de datos y algunos datos extras referentes a la tabla, estas tablas pueden ser vistas desde *Glue Data Catalog* o desde cualquier servicio que cuente con integración a este como lo es el servicio de *Athena*.
    
    Una caracteristica interesante de los crawlers es que cuentan con versionamiento de tablas, es decir, que si el crawler es gatillado más de una vez y el source del crawler se modifico, este va a registrar una nueva versión en la tabla dejando registro de la versión anterior.

3. Crear bucket en S3

    * Ir al servicio **S3**
    * **Create bucket**
    * **Bucket name** *"bucket-glue-target-'nombre-apellido'"* (debe ser único para la región) 
    * **Create**
    * Repetir para bucket *"bucket-glue-libraries-'nombre-apellido'"* (debe ser único para la región)

4. Configurar Glue Job

    * Ir a AWS Glue -> **Jobs** -> **Add job**
    * Dar nombre *"my-job-glue"* y elegir rol creado
    * Saltar a la sección *This job runs*, seleccionar *A new script to be authored by you* -> **Next**
    * **Save job and edit script**
    * Copiar el contenido del archivo *"glue-job.py"*
    * Cambiar la *línea 22* al path del job de Glue creado *"s3://bucket-glue-target-'nombre-apellido'/target/productos"*
    * Hacer click en **Save** y luego en **Run job**

5. Lanzar crawler en target S3

    * Mismos pasos que en *"Paso 2"* con el nombre *"my-crawler-target"*
    * **Data store S3** *"s3://bucket-glue-target-'nombre-apellido'/target/productos"*
    * **Add database**, name *"db-target"* -> **Next** -> **Finish**
    * Click en **Run it now**

6. Integrar Deequ en Glue Job para análisis de data (Spark Scala)

    * Ir a S3 seleccionar bucket *"bucket-glue-libraries-'nombre-apellido'"*, **Create folder** nombre *"jar"*, entrar a la carpeta creada y dar click en **Upload**, seleccione el JAR *"deequ-1.0.1.jar"* y click en **Upload**
    * Ir a AWS Glue -> **Jobs** -> **Add job**
    * Dar nombre *"my-job-analize-deequ"* y elegir rol creado
    * Cambiar **Glue version** a *Spark 2.4, Scala (Glue version 1.0)
    * Cambiar **This job runs** a *A new script to be authored by you*
    * En **Scala class name** escribir *"GlueApp"*
    * **Script file name** *"glue-deequ-integration-analize"*
    * En **Security configuration, script libraries, and job parameters (optional)**, **Dependent jars path** *"s3://bucket-glue-libraries-'nombre-apellido'/jar/deequ-1.0.1.jar"* -> **Next**
    * **Save job and edit script**
    * Copiar el código del archivo *"glue-analize-deequ.scala"*
    * Cambiar en la *línea 19* al path *"s3://bucket-glue-target-'nombre-apellido'/target/deequ/productos_analysis"*
    * Cambiar la *línea 21* al path del job de Glue creado *"s3://bucket-glue-target-'nombre-apellido'/target/productos"*
    * **Save**
    * **Run job**

7. Editar crawler target

    Para poder visualizar los resultados en Athena, estos deben haber sido posteriormente agregados al Catálogo de Glue

    * Ir al crawler del **target** -> **Edit**
    * **Next** -> **Next** -> **Next**
    * En *Add another data store* elegir **Yes** -> **Next**
    * En *Include path* escribir *"s3://bucket-glue-target-'nombre-apellido'/target/deequ/productos_analysis"* -> **Next** hasta terminar el proceso
    * Correr el crawler nuevamente para que se añada la nueva tabla al Catálogo de Glue


8. Consultar resultados con Athena

    * Ir al servicio **Athena**
    * Seleccionar base *db-target*
    * Para ver los registros de una tabla haga una consulta SQL sobre esta

9. Integrar Deequ en Glue Job para testing de data (Spark Scala)

    * Ir a AWS Glue -> **Jobs** -> **Add job**
    * Dar nombre *"MY-job-validate-deequ"* y elegir rol creado
    * Cambiar **Glue version** a *Spark 2.4, Scala (Glue version 1.0)
    * Cambiar **This job runs** a *A new script to be authored by you*
    * En **Scala class name** escribir *"GlueApp"*
    * **Script file name** *"glue-deequ-integration-validate"*
    * En **Security configuration, script libraries, and job parameters (optional)**, **Dependent jars path** *"s3://bucket-glue-libraries-'nombre-apellido'/jar/deequ-1.0.1.jar"* -> **Next**
    * **Save job and edit script**
    * Copiar el código del archivo *"script-glue-deequ.scala"*
    * Cambiar en la *línea 19* al path *"s3://bucket-glue-target-'nombre-apellido'/target/deequ/productos_results"*
    * Cambiar la *línea 21* al path del job de Glue creado *"s3://bucket-glue-target-'nombre-apellido'/target/productos"*
    * **Save**
    * **Run job**

10. Editar crawler target

    Para poder visualizar los resultados en Athena, estos deben haber sido posteriormente agregados al Catálogo de Glue

    * Ir al crawler del **target** -> **Edit**
    * **Next** -> **Next** -> **Next**
    * En *Add another data store* elegir **Yes** -> **Next**
    * En *Include path* escribir *"s3://bucket-glue-target-'nombre-apellido'/target/deequ/productos_results"* -> **Next** hasta terminar el proceso
    * Correr el crawler nuevamente para que se añada la nueva tabla al Catálogo de Glue

11. Consultar resultados con Athena

    * Ir al servicio **Athena**
    * Seleccionar base *db-target*
    * Para ver los registros de una tabla haga una consulta SQL sobre esta

12. Automatizando el flujo

    Para conseguir optimizar los tiempos de cara a la agilidad del negocio Glue ofrece características de automatización, como lo son los *Triggers* y los *Workflows*

    Para añadir un trigger es necesario seguir estos pasos:

    * Ir a la consola de Glue al apartado *Triggers* -> *Add trigger*
    * Dar un nombre al trigger
    * Configurar *Trigger type* a *Job Events*
    * Seleccionar el job *"my-job-glue"* -> **Next**
    * En *Choose jobs to trigger* seleccionar el job *"my-job-analize-deequ"* -> **Next** -> **Finish**

    Ahora cada vez que el job *"my-job-glue"* sea ejecutado, al final de su ejecución y si esta es exitosa el job *"my-job-analize-deequ"* iniciará