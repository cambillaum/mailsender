name := "mailsender"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "javax.mail" % "mail" % "1.4.7"

lazy val mailsender = (project in file(".")).settings(
  mainClass in assembly := Some("cambillaum.mailsender.MailSender")
)