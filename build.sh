./gradlew clean build
for p in postgres mysql oracle; do java -jar -Dspring.batch.job.enabled=false -Dspring.profiles.active=$p build/libs/netflixdb-0.0.1-SNAPSHOT.jar; done
