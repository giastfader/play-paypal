package controllers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;

import models.PaypalTransaction;
import play.Logger;
import play.mvc.Controller;

/**
 * 
 * Paypal controller
 * 
 * @author guillaumeleone
 *
 */
public class PaypalController extends Controller {

	/**
	 * 
	 * PayPal sends your IPN listener a message that notifies you of the event
	 * 
	 * Your listener sends the complete unaltered message back to PayPal; the message must contain 
	 * the same fields in the same order and be encoded in the same way as the original message
	 * 
	 * PayPal sends a single word back, which is either VERIFIED if the message originated with PayPal or 
	 * INVALID if there is any discrepancy with what was originally sent
	 * 
	 * @throws Exception
	 */
	public static void validation() throws Exception {
		
		Logger.info("validation");
		
		// creation de l'url a envoyé à paypal pour vérification
		//on recupere les parametres de la requetes POST
		String str = "cmd=_notify-validate&" + params.get("body");
		Logger.info(str);
		
		//création d'une connection à la sandbox paypal
		URL url = new URL("https://www.sandbox.paypal.com/cgi-bin/webscr");
		URLConnection connection = url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
		
		//envoi de la requête
		PrintWriter out = new PrintWriter(connection.getOutputStream());
		out.println(str);
		out.close();

		//lecture de la réponse
		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String result = in.readLine();
		in.close();
		
		// assign posted variables to local variables
		String itemName = params.get("item_name");
		String itemNumber = params.get("item_number");
		String paymentStatus = params.get("payment_status");
		String paymentAmount = params.get("mc_gross");
		String paymentCurrency = params.get("mc_currency");
		String txnId = params.get("txn_id");
		String receiverEmail = params.get("receiver_email");
		String payerEmail = params.get("payer_email");

		//check notification validation
		if("VERIFIED".equals(result)) {
			if ("Completed".equals(paymentStatus)) {
				// on vérife que la txn_id n'a pas été traité précédemment
				PaypalTransaction paypalTransaction = PaypalTransaction.findByTrxId(txnId);
				// si aucune transaction en base ou si transaction mais en status invalide
				// on traite la demande
				if (paypalTransaction == null 
						|| (paypalTransaction != null && PaypalTransaction.TrxStatusEnum.INVALID.equals(paypalTransaction.status))) {
					// on vérifie que receiver_email est votre adresse email
					// a remplacer par l'adresse mail du vendeur
					if ("seller@paypalsandbox.com".equals(receiverEmail)) {
						// vérifier que paymentAmount (EUR) et paymentCurrency (prix du produit vendu) sont corrects
						Logger.info("Transaction OK");
						//sauvegarde la trace de la transaction paypal en base
						new PaypalTransaction(itemName, itemNumber, paymentStatus, paymentAmount, paymentCurrency, txnId, receiverEmail, payerEmail,PaypalTransaction.TrxStatusEnum.VALID).save();
					} else {
						// Mauvaise adresse email paypal
						Logger.info("Mauvaise adresse email paypal");
					}
				} else {
					// ID de transaction déjà utilisé
					Logger.info("La transaction a déjà été traité");
				}
			} else {
				// Statut de paiement: Echec
				Logger.info("Statut de paiement: Echec");
			}
		} else if("INVALID".equals(result)) {
			Logger.info("Invalide transaction");
			new PaypalTransaction(itemName, itemNumber, paymentStatus, paymentAmount, paymentCurrency, txnId, receiverEmail, payerEmail,PaypalTransaction.TrxStatusEnum.INVALID).save();
		} else {
			Logger.info("Erreur lors du traitement");
		}
    }
	
	/** Success payment */
	public static void success(){
		Logger.info("success");
		render();
	}
	
	/** home page */
	public static void buy(){
		Logger.info("buy");
		render();
	}
	
	/** fail payment */
	public static void fail(){
		Logger.info("fail");
		render();
	}
}
----------
.content .form{
	padding:30px;	
}
.content .title-sub{
	border-bottom:1px dotted #777;
}
.content .title-sub h3{
	position:relative;
	top:10px;
	width:230px;
	color:#FFF;
	background-color:#999;
	padding:3px 10px;
	font-size:13px;
}
.content .content-sub{
	padding:30px 10px;
}
.content .content-sub .left{
	float:left;
	width:220px;
	height:100%;
	padding-top:10px;
}
.content .content-sub .left .img{
	width:135px;
	height: 135px;
	margin:0 auto;
	text-align:center;
	border:7px solid #F0F0F0;
}
.content .content-sub .left .img img{
	border-color: #E9E9E9 #E9E9E9 -moz-use-text-color;
}
.content .content-sub .left .img .back{
	background-color:#000;
	padding-left:2px;
	opacity:0.8;
	position:relative;
	top:-26px;
	height:23px;
}
.content .content-sub .left .img .back a{
	color:#FFF;
	display:block;
	padding-top:4px;
	font-size:11px;
}
.content .content-sub .msg-photo-profil{
	width:600px;
	padding-top:30px;	
}
.content .content-sub .msg-photo-profil input[type="checkbox"]{
	cursor:pointer;
	margin-left:10px;
}
.content .content-sub .msg-photo-profil a{
	text-decoration:underline;
}
.content .content-sub .msg-photo-profil span{
	position:relative;
	bottom:1px;
	padding-left:5px;
	font-weight:bold;
}
.content .content-sub .content-sub-info{
	height:205px;	
}
.content .content-sub .content-loader-form{
	height:205px;	
}
.content .content-sub .content-loader-form .center-loader{
	width:10px;
	margin:0 auto;	
}
.content .content-sub .track-more-info {
	padding-top:20px;	
}
.content .content-sub .track-more-info .left-track-more-info{
	float:left;
	width:280px;
	padding:20px;
}
.content .content-sub .track-more-info .left-track-more-info p{
}
.content .content-sub .track-more-info .left-track-more-info label{
	width:150px;
}
.content .content-sub .content-loader-form .center-loader .frame {
    display: table-cell;
    vertical-align: middle;
    height:205px;
}
.content .content-sub label{
	width:250px;
	display:block;
	float:left;
}
.content .content-sub input[type="text"],.content .content-sub input[type="password"]{
	width:350px;
	height:23px;
	padding-left:5px;
}
.content .content-sub textarea{
	width:600px;
	height:80px;
	padding-left:5px;
	padding-top:2px;
}
.content .button-submit{
	text-align:right;
}
#clearTrackForm{
	font-size:11px;
	color:#555;
	cursor:pointer;
	padding-right:5px;	
}
.content-sub .song{
	
}
.content-sub .song .sep{
	float:left;
	height:20px;
	border-left:1px dotted #777;
	margin:11px 10px 0 10px;
	width:1px;
}
.content-sub .song .like{
	float:left;
	width:70px;
	text-align:center;
}
.content-sub .song .edit{
	float:left;
	width:70px;
	padding-left:20px;
}	
.content-sub .song .delete{
	float:left;
	width:70px;
}
#month{
	width:116px;
}
#year{
	width:117px;
}
#day{
	width:117px;
}

-------------------

	<div class="title-sub">
			<h3>Votre photo de profil</h3>
		</div>
		<div class="content-sub">
			<div class="left">
				<div class="img">
					<img src="@{'/public/images/avatar.png'}" />
					<div class="back">
						<a id="upload-photo" href="#container-upload-photo"><b>Modifier la photo</b></a>
					</div>
				</div>
			</div>
			<div class="left msg-photo-profil">
				<p>
					Pour simplifier la procédure, vous pouvez aussi utiliser l'outils <a href="http://fr.gravatar.com/" about="_blank">Gravatar</a> (Globally Recognized Avatars).
					<br/><br/>
					Vous associez un email à compte gravatar. Et les sites, utilisant les API Gravatar, reconnaitront automatiquement votre avatar (image de profil) et l'associeront à votre compte.
					<br/><br/>
					<input type="checkbox" value="useGravatar" name="useGravatar" id="useGravatar" /><span>Utilisez Gravatar</span>
				</p>
			</div>
			<div class="clearboth"></div>
		</div>