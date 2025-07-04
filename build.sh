./gradlew clean build
for p in h2 postgres mysql oracle sqlserver sqlite; do java -jar -Dspring.profiles.active=$p build/libs/netflixdb-0.0.1-SNAPSHOT.jar; done
