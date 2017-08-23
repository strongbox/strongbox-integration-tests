import org.carlspring.strongbox.client.RestClient
import org.carlspring.strongbox.artifact.generator.NugetPackageGenerator
import java.nio.file.Paths
import java.nio.file.Files

println "Test common-nuget-flow.groovy" + "\n\n"

def targetDir = project.build.directory
println "Target directory: $targetDir\n\n"

def targetPath = Paths.get(targetDir).resolve('nuget-it')
Files.createDirectories(targetPath)

def runCommand = { strList ->
    assert (strList instanceof String || (strList instanceof List && strList.each{ it instanceof String } ))

    def path = targetPath.toFile()
        
    println "Execute command[s]: "
    if(strList instanceof List) {
      strList.each{ println "${it} " }
    } else {
      println strList
    }
    
    def proc = strList.execute(null, path)
    proc.in.eachLine { line -> println line }
    proc.out.close()
    proc.waitFor() 
  
    println "\n"
    
    if (proc.exitValue()) {
      println "gave the following error: "
      println "[ERROR] ${proc.getErrorStream()}"
    }
    
    assert !proc.exitValue()
}

def nugetExec = System.getenv("NUGET_V2_EXEC")
assert nugetExec?.trim() : "\"NUGET_V2_EXEC\" environment variable need to be set"

def packageId = "Org.Carlspring.Strongbox.Examples.Nuget.Mono" 
def packageVersion = "1.0.0"
def packageFileName = packageId + "." + packageVersion + ".nupkg";

def baseDir = targetPath.toString()

def nugetPackageGenerator = new NugetPackageGenerator(baseDir);
nugetPackageGenerator.generateNugetPackage(packageId, packageVersion);
def packageFilePath = Paths.get(baseDir).resolve(packageVersion).resolve(packageFileName);

def client = RestClient.getTestInstanceLoggedInAsAdmin()

println "Host name: " + client.getHost()
println "Username:  " + client.getUsername()
println "Password:  " + client.getPassword() + "\n\n"

def nugetApiKey = client.generateUserSecurityToken();
println "ApiKey: $nugetApiKey\n\n"

def storageUrl = String.format("%s/storages/nuget-common-storage/nuget-releases", client.getContextBaseUrl()) 
println "Storage URL:  $storageUrl\n\n"
   
def configPath = "$baseDir/NuGet.config"

new File(configPath).newWriter().withWriter { w ->
  w << ("<?xml version=\"1.0\" encoding=\"utf-8\"?><configuration></configuration>")
}

runCommand(String.format(
    "mono --runtime=v4.0 $nugetExec sources Add -Name %s -Source %s -UserName %s -Password %s -ConfigFile %s",
    "strongbox",
    storageUrl,
    "admin",
    "password",
    configPath))
runCommand(String.format(
    "mono --runtime=v4.0 $nugetExec config -set DefaultPushSource=%s -ConfigFile %s",
    storageUrl,
    configPath))
runCommand(String.format(
    "mono --runtime=v4.0 $nugetExec setApiKey %s -Source %s -ConfigFile %s",
    nugetApiKey,
    storageUrl,
    configPath))
runCommand(String.format(
    "mono --runtime=v4.0 $nugetExec push %s/%s -ConfigFile %s",
    packageVersion,
    packageFileName,
    configPath))