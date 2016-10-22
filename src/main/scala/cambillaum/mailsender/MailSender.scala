package cambillaum.mailsender

import java.io.File
import java.lang.Boolean
import java.util.Properties
import java.util.concurrent.Callable
import javafx.application.{Application, Platform}
import javafx.beans.binding.Bindings
import javafx.beans.binding.Binding
import javafx.beans.property._
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.event.{ActionEvent, EventHandler}
import javafx.geometry.{Insets, Pos}
import javafx.scene.control.Alert.AlertType
import javafx.scene.{Group, Scene}
import javafx.scene.control._
import javafx.scene.layout.{GridPane, HBox}
import javafx.stage.{FileChooser, Stage}
import javafx.util.StringConverter
import javax.activation.{DataHandler, FileDataSource}
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}


class MailSender extends Application {

  override def start(primaryStage: Stage): Unit = {
    val model = MailSenderModel()
    val viewModel = MailSenderViewModel(model)
    viewModel.show(primaryStage)

  }

}

object MailSender {

  def main(args: Array[String]): Unit = {
    Application.launch(classOf[MailSender], args:_*)
  }

}

case class MailSenderViewModel(model: MailSenderModel) {

  def show(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Email Sender")
    val gridPane = new GridPane()
    gridPane.setHgap(10)
    gridPane.setVgap(10)
    gridPane.setPadding(new Insets(10, 10, 10, 10))
    gridPane.setAlignment(Pos.TOP_CENTER)

    val smtpHostLabel = new Label("Serveur SMTP")
    gridPane.add(smtpHostLabel, 0, 0)
    val smtpHostTextField = new TextField()
    smtpHostTextField.textProperty().bindBidirectional(model.smtpHostProperty)
    val smtpHostValidationLabel = new Label("Serveur SMTP ne peut pas être vide")
    smtpHostValidationLabel.visibleProperty().bind(model.smtpHostValidationLabelVisible)
    val smtpHostHbox = new HBox(10, smtpHostTextField, smtpHostValidationLabel)
    gridPane.add(smtpHostHbox, 1, 0)

    val smtpPortLabel = new Label("Port SMTP")
    gridPane.add(smtpPortLabel, 0, 1)
    val smtpPortTextField = new TextField()
    smtpPortTextField.textProperty().addListener(numberChangeListener(smtpPortTextField))
    smtpPortTextField.textProperty().bindBidirectional(model.maybeSmtpPortProperty, maybeNumberStringConverter(model.maybeSmtpPortProperty))
    val smtpPortValidationLabel = new Label("Port SMTP ne peut pas être vide")
    smtpPortValidationLabel.visibleProperty().bind(model.smtpPortValidationLabelVisible)
    val smtpPortHbox = new HBox(10, smtpPortTextField, smtpPortValidationLabel)
    gridPane.add(smtpPortHbox, 1, 1)

    val userNameLabel = new Label("Utilisateur")
    gridPane.add(userNameLabel, 0, 2)
    val userNameTextField = new TextField()
    userNameTextField.textProperty().bindBidirectional(model.maybeUserNameProperty, maybeInternetAddressStringConverter)
    val userNameValidationLabel = new Label("Utilisateur non valide")
    userNameValidationLabel.visibleProperty().bind(model.userNameValidationLabelVisible)
    val userNameHbox = new HBox(10, userNameTextField, userNameValidationLabel)
    gridPane.add(userNameHbox, 1, 2)

    val passwordLabel = new Label("Mot de passe")
    gridPane.add(passwordLabel, 0, 3)
    val passwordField = new PasswordField()
    passwordField.textProperty().bindBidirectional(model.passwordProperty)
    val passwordValidationLabel = new Label("Mot de pass ne peut pas être vide")
    passwordValidationLabel.visibleProperty().bind(model.passwordValidationLabelVisible)
    val passwordHbox = new HBox(10, passwordField, passwordValidationLabel)
    gridPane.add(passwordHbox, 1, 3)

    val senderLabel = new Label("Envoyeur")
    gridPane.add(senderLabel, 0, 4)
    val senderTextField = new TextField()
    senderTextField.textProperty().bindBidirectional(model.maybeSenderProperty, maybeInternetAddressStringConverter)
    val senderValidationLabel = new Label("Envoyeur non valide")
    senderValidationLabel.visibleProperty().bind(model.senderValidationLabelVisible)
    val senderHBox = new HBox(10, senderTextField, senderValidationLabel)
    gridPane.add(senderHBox, 1, 4)

    val recipientsLabel = new Label("Destinataires")
    gridPane.add(recipientsLabel, 0, 5)
    val recipientsTextArea = new TextArea("recipient1@gmail.com\nrecipient2@gmail.com")
    recipientsTextArea.textProperty().bindBidirectional(model.recipientsProperty, new StringConverter[Seq[Option[InternetAddress]]] {
      override def fromString(string: String): Seq[Option[InternetAddress]] = string.split('\n').filter(line => line.trim.nonEmpty).map(line => Try(new InternetAddress(line.trim)).toOption)
      override def toString(internetAddresses: Seq[Option[InternetAddress]]): String = internetAddresses.map(maybeInternetAddress => maybeInternetAddress.map(internetAddress => internetAddress.getAddress).getOrElse("")).mkString("\n")
    })
    recipientsTextArea.setPrefRowCount(3)
    val recipientsValidationLabel = new Label("Destinataires non valides")
    recipientsValidationLabel.visibleProperty().bind(model.recipientsValidationLabelVisible)
    val recipientsHBox = new HBox(10, recipientsTextArea, recipientsValidationLabel)
    gridPane.add(recipientsHBox, 1, 5)

    val subjectLabel = new Label("Sujet")
    gridPane.add(subjectLabel, 0, 6)
    val subjectTextField = new TextField()
    subjectTextField.textProperty().bindBidirectional(model.subjectProperty)
    val subjectValidationLabel = new Label("Sujet ne peut pas être vide")
    subjectValidationLabel.visibleProperty().bind(model.subjectValidationLabelVisible)
    val subjectHBox = new HBox(10, subjectTextField, subjectValidationLabel)
    gridPane.add(subjectHBox, 1, 6)

    val bodyLabel = new Label("Message")
    gridPane.add(bodyLabel, 0, 7)
    val bodyTextArea = new TextArea()
    bodyTextArea.textProperty().bindBidirectional(model.bodyProperty)
    val bodyValidationLabel = new Label("Message ne peut pas être vide")
    bodyValidationLabel.visibleProperty().bind(model.bodyValidationLabelVisible)
    val bodyHBox = new HBox(10, bodyTextArea, bodyValidationLabel)
    gridPane.add(bodyHBox, 1, 7)

    val attachmentsLabel = new Label("Attachements")
    gridPane.add(attachmentsLabel, 0, 8)
    val attachmentsButton = new Button("Choisir")
    attachmentsButton.setOnAction(new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent): Unit = {
        import scala.collection.JavaConversions._
        val fileChooser = new FileChooser()
        val filesOrNull = fileChooser.showOpenMultipleDialog(primaryStage)
        val files: Seq[File] = if(filesOrNull eq null) Seq.empty[File] else filesOrNull
        model.filesProperty.set(files)
      }
    })
    val attachmentsInformationLabel = new Label()
    attachmentsInformationLabel.textProperty().bind(model.attachmentsInformationLabelText)
    val attachmentsInformationGroup = new Group(attachmentsInformationLabel)
    val attachmentsHBox = new HBox(10, attachmentsButton, attachmentsInformationGroup)
    gridPane.add(attachmentsHBox, 1, 8)

    val attachmentsPerEmailLabel = new Label("Attachements par email")
    gridPane.add(attachmentsPerEmailLabel, 0, 9)
    val attachmentsPerEmailTextField = new TextField()
    attachmentsPerEmailTextField.textProperty().addListener(numberChangeListener(attachmentsPerEmailTextField))
    attachmentsPerEmailTextField.textProperty().bindBidirectional(model.maybeAttachmentsPerEmailProperty, maybeNumberStringConverter(model.maybeAttachmentsPerEmailProperty))
    val attachmentsPerEmailValidationLabel = new Label("Attachements par email ne peut pas être vide")
    attachmentsPerEmailValidationLabel.visibleProperty().bind(model.attachmentsPerEmailValidationLabelVisible)
    val attachmentsPerEmailHBox = new HBox(10, attachmentsPerEmailTextField, attachmentsPerEmailValidationLabel)
    gridPane.add(attachmentsPerEmailHBox, 1, 9)


    val sendButton = new Button("Envoyer")
    sendButton.disableProperty().bind(model.sendButtonDisabled)
    sendButton.setAlignment(Pos.CENTER_RIGHT)
    sendButton.setOnAction(new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent): Unit = {
        model.beforeSend
        val sendFuture = model.send
        sendFuture.onComplete {
          case Success(_) => Platform.runLater(new Runnable {
            override def run(): Unit = onSendCompletion
          })
          case Failure(_) => Platform.runLater(new Runnable {
            override def run(): Unit = onSendCompletion
          })
        }

      }
    })
    val sendButtonHBox = new HBox(sendButton)
    sendButtonHBox.setAlignment(Pos.CENTER_RIGHT)
    gridPane.add(sendButtonHBox, 1, 11)

    val scene = new Scene(gridPane)
    primaryStage.setScene(scene)
    primaryStage.show()
  }

  private def onSendCompletion: Unit = {
    model.afterSend
    val alert = new Alert(AlertType.INFORMATION)
    alert.setHeaderText(null)
    alert.setTitle("Emails envoyés")
    alert.setContentText("Les emails ont été envoyés!")
    alert.showAndWait()
  }

  private def maybeInternetAddressStringConverter: StringConverter[Option[InternetAddress]] = new StringConverter[Option[InternetAddress]] {
    override def fromString(string: String): Option[InternetAddress] = Try(new InternetAddress(string)).toOption
    override def toString(maybeInternetAddress: Option[InternetAddress]): String = maybeInternetAddress.map(internetAddress => internetAddress.getAddress).getOrElse("")
  }

  private def maybeNumberStringConverter(property: ObjectProperty[Option[Long]]): StringConverter[Option[Long]] = new StringConverter[Option[Long]] {
    override def fromString(string: String): Option[Long] = if(string.trim.isEmpty) None else Some(string.toLong)
    override def toString(maybeNumber: Option[Long]): String = maybeNumber.map(number => number.toString).getOrElse("")
  }

  private def numberChangeListener(textField: TextField): ChangeListener[String] = new ChangeListener[String] {
    override def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
      if(newValue.trim.nonEmpty && Try(newValue.toLong).isFailure) {
        textField.setText(oldValue)
      }
    }
  }

}

case class MailSenderModel() {
  val mailSenderState: ObjectProperty[MailSenderState] = new SimpleObjectProperty[MailSenderState](Idle)
  val smtpHostProperty: StringProperty = new SimpleStringProperty("smtp.gmail.com")
  val smtpHostValid: BooleanProperty = new SimpleBooleanProperty()
  smtpHostValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = smtpHostProperty.get().trim.nonEmpty
  }, smtpHostProperty))
  val smtpHostValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  smtpHostValidationLabelVisible.bind(Bindings.not(smtpHostValid))
  val maybeSmtpPortProperty: ObjectProperty[Option[Long]] = new SimpleObjectProperty[Option[Long]](Some(587))
  val smtpPortValid: BooleanProperty = new SimpleBooleanProperty()
  smtpPortValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = maybeSmtpPortProperty.get().isDefined
  }, maybeSmtpPortProperty))
  val smtpPortValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  smtpPortValidationLabelVisible.bind(Bindings.not(smtpPortValid))
  val maybeUserNameProperty: ObjectProperty[Option[InternetAddress]] = new SimpleObjectProperty[Option[InternetAddress]](Some(new InternetAddress("someone@gmail.com")))
  val userNameValid: BooleanProperty = new SimpleBooleanProperty()
  userNameValid.bind(validInternetAddressBinding(maybeUserNameProperty))
  val userNameValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  userNameValidationLabelVisible.bind(Bindings.not(userNameValid))
  val passwordProperty: StringProperty = new SimpleStringProperty("")
  val passwordValid: BooleanProperty = new SimpleBooleanProperty()
  passwordValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = passwordProperty.get().trim.nonEmpty
  }, passwordProperty))
  val passwordValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  passwordValidationLabelVisible.bind(Bindings.not(passwordValid))
  val maybeSenderProperty: ObjectProperty[Option[InternetAddress]] = new SimpleObjectProperty[Option[InternetAddress]](Some(new InternetAddress("someone@gmail.com")))
  val senderValid: BooleanProperty = new SimpleBooleanProperty()
  senderValid.bind(validInternetAddressBinding(maybeSenderProperty))
  val senderValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  senderValidationLabelVisible.bind(Bindings.not(senderValid))
  val recipientsProperty: ObjectProperty[Seq[Option[InternetAddress]]] = new SimpleObjectProperty[Seq[Option[InternetAddress]]](Seq(Some(new InternetAddress("someone1@gmail.com")), Some(new InternetAddress("someone2@gmail.com"))))
  val recipientsValid: BooleanProperty = new SimpleBooleanProperty()
  recipientsValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = {
      recipientsProperty.get().map(maybeRecipient => maybeRecipient.exists(recipient => Try(recipient.validate()).isSuccess)).forall(identity)
    }
  }, recipientsProperty))
  val recipientsValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  recipientsValidationLabelVisible.bind(Bindings.not(recipientsValid))
  val subjectProperty: StringProperty = new SimpleStringProperty("")
  val subjectValid: BooleanProperty = new SimpleBooleanProperty()
  subjectValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = subjectProperty.get().trim.nonEmpty
  }, subjectProperty))
  val subjectValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  subjectValidationLabelVisible.bind(Bindings.not(subjectValid))
  val bodyProperty: StringProperty = new SimpleStringProperty("")
  val bodyValid: BooleanProperty = new SimpleBooleanProperty()
  bodyValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = bodyProperty.get().trim.nonEmpty
  }, bodyProperty))
  val bodyValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  bodyValidationLabelVisible.bind(Bindings.not(bodyValid))
  val filesProperty: ObjectProperty[Seq[File]] = new SimpleObjectProperty[Seq[File]](Seq.empty[File])
  val attachmentsValid: BooleanProperty = new SimpleBooleanProperty()
  attachmentsValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = filesProperty.get().nonEmpty
  }, filesProperty))
  val attachmentsInformationLabelText: StringProperty = new SimpleStringProperty()
  attachmentsInformationLabelText.bind(Bindings.createStringBinding(new Callable[String] {
    override def call(): String = if(attachmentsValid.get()) {
      filesProperty.get().map(file => file.getName).mkString("\n")
    } else {
      "Au moins un fichier doit être attaché"
    }
  }, filesProperty, attachmentsValid))
  val maybeAttachmentsPerEmailProperty: ObjectProperty[Option[Long]] = new SimpleObjectProperty[Option[Long]](Some(1))
  val attachmentsPerEmailValid: BooleanProperty = new SimpleBooleanProperty()
  attachmentsPerEmailValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = maybeAttachmentsPerEmailProperty.get.isDefined
  }, maybeAttachmentsPerEmailProperty))
  val attachmentsPerEmailValidationLabelVisible: BooleanProperty = new SimpleBooleanProperty()
  attachmentsPerEmailValidationLabelVisible.bind(Bindings.not(attachmentsPerEmailValid))
  val allValid: BooleanProperty = new SimpleBooleanProperty()
  allValid.bind(Bindings.createBooleanBinding(new Callable[Boolean] {
    override def call(): Boolean = {
      val correctState = mailSenderState.get() match {
        case Idle => true
        case Sending => false
      }
      correctState && smtpHostValid.get() && smtpPortValid.get() && userNameValid.get() && passwordValid.get() && senderValid.get() && recipientsValid.get() && subjectValid.get() && bodyValid.get() && attachmentsValid.get() && attachmentsPerEmailValid.get()
    }
  }, mailSenderState, smtpHostValid, smtpPortValid, userNameValid, passwordValid, senderValid, recipientsValid, subjectValid, bodyValid, attachmentsValid, attachmentsPerEmailValid))
  val sendButtonDisabled: BooleanProperty = new SimpleBooleanProperty()
  sendButtonDisabled.bind(Bindings.not(allValid))

  def beforeSend: Unit = mailSenderState.setValue(Sending)

  def afterSend: Unit = mailSenderState.setValue(Idle)

  def send: Future[Unit] = {
    Future {
      val sender = DefaultSender(maybeUserNameProperty.get().get.getAddress, passwordProperty.get(), smtpHostProperty.get().trim, maybeSmtpPortProperty.get().get)
      val multiAttachmentsSender = DefaultMultiAttachmentsMailSender(sender)
      val from = maybeSenderProperty.get().get
      val recipients = recipientsProperty.get().flatten
      val subject = subjectProperty.get()
      val body = bodyProperty.get()
      val attachments = filesProperty.get()
      multiAttachmentsSender.send(from, recipients, subject, body, attachments, maybeAttachmentsPerEmailProperty.get().get.toInt)
    }
  }

  private def validInternetAddressBinding(maybeInternetAddressProperty: ObjectProperty[Option[InternetAddress]]): Binding[Boolean] = {
    Bindings.createBooleanBinding(new Callable[Boolean] {
      override def call(): Boolean = maybeInternetAddressProperty.get().exists(internetAddress => Try(internetAddress.validate()).isSuccess)
    }, maybeInternetAddressProperty)
  }

}

sealed trait MailSenderState
case object Idle extends MailSenderState
case object Sending extends MailSenderState

trait Sender {
  def send(from: InternetAddress, recipients: Seq[InternetAddress], subject: String, body: String, attachments: Seq[File], maybeReplyTo: Option[Message] = None): Message
}

class DefaultSender(private val userName: String, private val password: String, private val smtpHost: String, private val smtpPort: Long) extends Sender {

  override def send(from: InternetAddress, recipients: Seq[InternetAddress], subject: String, body: String, attachments: Seq[File], maybeReplyTo: Option[Message] = None): Message = {
    val props = createProps
    val session = createSession(props)
    val message = createMessage(session, from, recipients, subject, body, attachments, maybeReplyTo)
    Transport.send(message)
    message
  }

  private def createMessage(session: Session, from: InternetAddress, recipients: Seq[InternetAddress], subject: String, body: String, attachments: Seq[File], maybeReplyTo: Option[Message] = None): Message = {
    val message = maybeReplyTo.map { replyTo =>
      replyTo.reply(false)
    } getOrElse {
      new MimeMessage(session)
    }
    message.setFrom(from)
    message.setRecipients(Message.RecipientType.TO, recipients.toArray[Address])
    message.setSubject(subject)
    val multipart = createMultipart(body, attachments)
    message.setContent(multipart)
    message
  }

  private def createMultipart(body: String, attachments: Seq[File]): Multipart = {
    val multipart = new MimeMultipart()
    val bodyPart = new MimeBodyPart()
    bodyPart.setText(body)
    val messageBodyParts = bodyPart +: attachments.map(attachment => createMessageBodyPart(attachment))
    messageBodyParts.foreach(messageBodyPart => multipart.addBodyPart(messageBodyPart))
    multipart
  }

  private def createMessageBodyPart(attachment: File): MimeBodyPart = {
    val messageBodyPart = new MimeBodyPart()
    val source = new FileDataSource(attachment)
    messageBodyPart.setDataHandler(new DataHandler(source))
    messageBodyPart.setFileName(attachment.getName)
    messageBodyPart
  }

  private def createSession(props: Properties): Session = {
    Session.getInstance(props, new Authenticator {
      override protected def getPasswordAuthentication: PasswordAuthentication = new PasswordAuthentication(userName, password)
    })
  }

  private def createProps: Properties = {
    val props = new Properties()
    props.put("mail.smtp.auth", true.asInstanceOf[AnyRef]);
    props.put("mail.smtp.starttls.enable", true.asInstanceOf[AnyRef]);
    props.put("mail.smtp.host", smtpHost.asInstanceOf[AnyRef]);
    props.put("mail.smtp.port", smtpPort.asInstanceOf[AnyRef]);
    props
  }
}

trait MultiAttachmentsMailSender {
  def send(from: InternetAddress, recipients: Seq[InternetAddress], subject: String, body: String, attachments: Seq[File], attachmentsPerEmail: Int, repeatBodyOnAllMails: Boolean = false): Unit
}

class DefaultMultiAttachmentsMailSender(sender: Sender) extends MultiAttachmentsMailSender{

  override def send(from: InternetAddress, recipients: Seq[InternetAddress], subject: String, body: String, attachments: Seq[File], attachmentsPerEmail: Int, repeatBodyOnAllMails: Boolean = false): Unit = {
    val groups = attachments.grouped(attachmentsPerEmail)
    groups.zipWithIndex.foldLeft(None: Option[Message]) { case (maybeReplyTo, (group, index)) =>
      val attachments = group.toSeq
      val actualBody = if(repeatBodyOnAllMails || index == 0) body else ""
      val actualSubject = if(index == 0) subject else s"Re: ${subject}"
      val sent = sender.send(from, recipients, actualSubject, actualBody, attachments, maybeReplyTo)
      Some(sent)
    }
  }

}

object DefaultMultiAttachmentsMailSender {
  def apply(sender: Sender) = new DefaultMultiAttachmentsMailSender(sender)
}

object DefaultSender {
  def apply(userName: String, password: String, smtpHost: String, smtpPort: Long) = new DefaultSender(userName, password, smtpHost, smtpPort)
}
