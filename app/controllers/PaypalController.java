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
package controllers;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import models.Genre;
import models.IpTrack;
import models.Songoftheday;
import models.Track;
import models.User;
import notifiers.Mail;

import org.apache.commons.validator.EmailValidator;

import play.Play;
import play.libs.Crypto;
import play.modules.paginate.ValuePaginator;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Http;

import common.APP_VAR;

/**
 * 
 * Controlleur principale de l'application
 * 
 * @author guillaumeleone
 * 
 */
public class AppController extends Controller {

	/**
	 * la methode est executé avant chaque action
	 */
	@Before
	static void addDefaults() {
		renderArgs.put("titleApplication",Play.configuration.getProperty("application.title"));
		renderArgs.put("version",Play.configuration.getProperty("application.version"));
		renderArgs.put("date",Play.configuration.getProperty("application.date"));
		
		//vide le cache de validation 
		validation.clear();
		
		//recupere les cookie de l'utilisateur
		Map<java.lang.String, Http.Cookie> info = response.cookies;
		renderArgs.put("cookies", info);
		
		if(Security.isConnected()){
			User user = User.find("byUsername", Security.connected()).first();
			if(user.isConfirmed){
				renderArgs.put("user", user);
			}
		}
	}

	/** index principale de la home page */
	public static void index() {
		
		// regenere la chanson du jour
		Songoftheday.checkSongoftheday();
		// affiche la sotd 
		Songoftheday sotd = Songoftheday.findSongoftheday();
		
		//solution en attendant; pour tjs avoir une SOTD
		if(sotd == null){
			sotd = Songoftheday.findById(new Long(1));
		}
		
		// on recupere la liste des tracks présentes en base 
		List<Track> tracks = Track.findByDateOrderByDesc();
	    ValuePaginator paginator = new ValuePaginator(tracks);
	    paginator.setPagesDisplayed(5);
	    paginator.setPageSize(5);
	    
	    // recupere la liste des catégories
	    List<Genre> genres = Genre.findAll();
	   
	    // recupere les 10 tracks les plus votés
	    List<Track> top5 = Track.findTop(5);
	    
	    // recupere les 10 tracks les plus votés dans le mois
	    List<Track> top5Month = Track.findTopMonth(5);
	    
	    boolean isErrorTrack = false;
	    
	    renderArgs.put("home", true);
	    render(paginator,sotd,genres,isErrorTrack,top5,top5Month);
	}
	
	public static final String ALL = "all";
	public static final String GENERAL = "general";
	public static final String MONTH = "month";
	public static final String SOTD = "sotd";
	
	/**
	 * 	affiche toutes les musiques par ordre decroissant en fonction de la date d'ajout
	 * 
	 *  - gestion de la pagination : 20 par pages
	 */
	public static void all(String id){
		
		List<Track> tracks = null;
		StringBuilder title = new StringBuilder();
		
		if(ALL.equals(id)){
			tracks = Track.findByDateOrderByDesc();
			title.append("Liste des musiques");
		} else if(GENERAL.equals(id)){
			tracks = Track.findTop(null);
			title.append("TOP Générale");
		} else if(MONTH.equals(id)){
			tracks = Track.findTopMonth(null);
			title.append("TOP pour le mois");
		} else if(SOTD.equals(id)){
			tracks = Track.findTopMonth(null);
			title.append("TOP pour le mois");
		}
		
	    ValuePaginator paginator = new ValuePaginator(tracks);
	    paginator.setPagesDisplayed(20);
	    paginator.setPageSize(20);
	    
		render(paginator,title);
	}
	
	/** ajoute -1 au vote d'une musique */
	public static void downVote(Long id) throws Exception {
	
		//recupere la track
		Track track = Track.findById(id);
		// check si aucun vote a été effectué
		if(!checkTrackToCookie(id)){
			if (track.diff() != 0) {
				track.downvote++;
				track.save();
				//save user ip
				setInfoCookie(id);
			}
		}
	
		//diff entre + et -
		int result = track.diff();
		renderJSON(result);
	}
	
	/** ajoute +1 au vote d'une musique et enregistre dans un cookie le vote */
	public static void upVote(Long id) throws Exception {

		Track track = Track.findById(id);
		
		// check si aucun vote a été effectué
		if(!checkTrackToCookie(id)){
			// +1
			track.upvote++;
			track.save();
			//save user ip
			setInfoCookie(id);
		}
		
		//calcul diff
		int result = track.diff();
		renderJSON(result);
	}
	
	/**
	 * - sauvegarde en base l'ip de l'utilisateur associé a la track
	 * - on ajoute dans le cookie la track id pour laquel il a voté 
	 */
	private static void setInfoCookie(Long id){
		//recherche si l'ip existe deja avec l id de la track
		IpTrack ipTrack = IpTrack.findByPublicAndTrack(request.remoteAddress,id);
		//si non on la sauvegarde
		if(ipTrack == null){
			new IpTrack(request.remoteAddress,id.toString()).save();
		}else{
			//si oui, on incremente le compteur
			ipTrack.count ++;
			ipTrack.save();
		}
		response.setCookie(Crypto.sign("track"+id), Crypto.sign(id.toString()), "1d");
	}
	
	/**
	 * parcourt les cookies de l'utilisateurs
	 * si aucun cookie on verifie en table si l'ip existe, on limite a 1 vote par ip
	 * 
	 * - false : autorisation de voter
	 * - true : ne peut pas voter
	 */
	public static boolean checkTrackToCookie(Long id){
		
		String nameCookie = Crypto.sign("track"+id);
		Http.Cookie trackCookie = request.cookies.get(nameCookie);
		
		//si le cookie n'existe pas
		if(trackCookie == null){
			
			//on verifie en base si l'ip n'existe pas pour la track
			IpTrack ipTrack = IpTrack.findByPublicAndTrack(request.remoteAddress,id);
			
			if(ipTrack == null){
				return false;
			}
			
			//si plus de 10 vote avec la mm ip pour une track
			if(ipTrack.count >= APP_VAR.MAX_VOTE_PER_DAY){
				return true;
			}
			
			return false;
		}
		
		String hashId = Crypto.sign(id.toString());
		
		if(hashId != null){
			if(hashId.equals(trackCookie.value)){
				return true;
			}
		}
		
		return false;
	}
	
	/** Formulaire de contact, envoi du mail */
	public static void contact(String contactname, String contactemail, String contactsubject,  String contactmessage) {
		
		validation.isTrue(contactname != null && contactemail != null
				&& contactsubject != null && contactmessage != null).message("Vous devez renseigner tous les champs");
		validation.isTrue(EmailValidator.getInstance().isValid(contactemail)).message("L'email saisi n'est pas valide");
		
		if(validation.hasErrors()) {
			params.flash(); 
		    validation.keep();
		    render("@AppController.index");
		}		
		
		// Envoi du message user
		Mail.contact(contactemail,contactname,contactsubject,contactmessage);
		
		flash.success("Votre email a bien été envoyé. Un membre de l'équipe va se charger de vous recontacter.");
		index();
	}
	
	/** affiche la page d'a propos */
	public static void about(){
		render();
	}
	
	/** signaler un pb sur une track */
	public static void alertTrack(Long id){
		String ip = request.remoteAddress;
		Mail.alert(id,ip);
		flash.success("Un message a été envoyé à notre équipe pour controller la musique");
		index();
	}
	
	/** affiche la page d'aide utilisateur */
	public static void help(){
		render();
	}
}
---------

package controllers;
 
import java.io.File;
import java.util.List;

import org.apache.commons.validator.EmailValidator;

import notifiers.Mail;
 
import common.SoundCloud;
import common.Utils;
 
import models.Genre;
import models.Songoftheday;
import models.Track;
import models.User;
import play.Logger;
import play.Play;
import play.data.validation.Required;
import play.modules.paginate.ValuePaginator;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.With;


/**
 * Controlleur permettant de gérer l'administration de l'application 
 */
@With(Secure.class)
public class AdminController extends Controller {

	/**
	 * la methode est executé avant chaque action
	 */
	@Before
	static void addDefaults() {
		renderArgs.put("titleApplication",Play.configuration.getProperty("application.title"));
		renderArgs.put("version",Play.configuration.getProperty("application.version"));
		renderArgs.put("date",Play.configuration.getProperty("application.date"));
		
		//vide le cache de validation 
		validation.clear();
		flash.clear();
		
		if(Security.isConnected()){
			User user = User.find("byUsername", Security.connected()).first();
			if(user != null && user.isConfirmed){
				renderArgs.put("user", user);
			}
		}
	}
	
    public static void index() {
    	User user = (User) renderArgs.get("user");
        render(user);
    }
    
	/** sauvegarde en base un nouveau post d'un utilisateur */
	public static void saveTrack(String title, Long genre, String description,
			@Required(message="Sélectionnez votre musique") File file){
	
		//utilisateur connecté
		User user = (User) renderArgs.get("user");

		validation.isTrue(user != null).message("Aucun utilisateur connecté");
		checkIsValidTrack(user,true);
		
		validation.isTrue(!"".equals(description) && file != null
				&& !"Renseignez le titre de votre musique".equals(title))
				.message("Pour envoyez une chanson vous devez renseignez tous les champs");
		checkIsValidTrack(user,true);
		
		//recupere le genre
		Genre cat = Genre.findById(genre);
		
		//upload sur le serveur soundcloud
		String link = null;
		Track track = null;
		try {
			link = SoundCloud.upload(title,file);
			//sauvegarde l'objet en base
			track = new Track(title,cat,link,user,description).save();
		} catch (Exception e) {
			Logger.error("error upload", e);
			validation.isTrue(link != null).message("Problème lors de l'envoie sur SoundCloud");
			checkIsValidTrack(user,true);
		}
		
		//success
		flash.success("Votre morceau a correctement été ajouté. Il est en attente de validation");
		
		//mail
		Mail.newTrack(track);
		
		//on affiche la home page
		displayHomePage(user, false);
	}

	/** verifie si le formulaire est valide */
	private static void checkIsValidTrack(User user, boolean flag){
		if(validation.hasErrors()){
			params.flash();
    		validation.keep();
    		displayHomePage(user,flag);
		}
	}

	/**
	 * affiche la page principale de l'application 
	 */
	private static void displayHomePage(User user, boolean flag){
		// affiche la sotd 
		Songoftheday sotd = Songoftheday.findSongoftheday();
		
		//solution en attendant; pour tjs avoir une SOTD
		if(sotd == null){
			sotd = Songoftheday.findById(new Long(1));
		}
		
		// on recupere la liste des tracks présentes en base 
		List<Track> tracks = Track.findByDateOrderByDesc();
	    ValuePaginator paginator = new ValuePaginator(tracks);
	    paginator.setPagesDisplayed(5);
	    paginator.setPageSize(5);
	    
	    // recupere la liste des catégories
	    List<Genre> genres = Genre.findAll();
		boolean isErrorTrack = flag;
		
	    renderArgs.put("home", true);
		render("@AppController.index",sotd,paginator,genres,user,isErrorTrack);
	}
	
	/**
	 * Mise à jour du profil utilisateur 
	 */
	public static void updateProfile(String fullname, String email, String website, String description,
			String oldpassword, String newpassword, String newpasswordconf) {

		User toUpdate = (User) renderArgs.get("user");

		validation.isTrue(toUpdate != null).message("Aucun utilisateur connecté");
		checkIsValidProfil(toUpdate);
		
    	validation.isTrue(EmailValidator.getInstance().isValid(email)).message("L'email saisi n'est pas valide");
    	checkIsValidProfil(toUpdate);
    	
		validation.isTrue(description.length() <= 300).message("La description ne doit pas dépasser 300 catactères");
		checkIsValidProfil(toUpdate);
		
		if (!"".equals(oldpassword)) {
			if (toUpdate.password.equals(User.encrypt(oldpassword)) 
					&& newpassword.equals(newpasswordconf)) {
				toUpdate.password = User.encrypt(newpassword);
			} else{
				validation.isTrue(false).message("L'ancien mot de passe est érroné");
				checkIsValidProfil(toUpdate);
			}
		}

		if (fullname != null) 
			toUpdate.fullname = fullname;
		
		if (website != null) 
			toUpdate.website = website;
	
		if (description != null) 
			toUpdate.description = description;
		
		if (email != null)
			toUpdate.email = email;

		// Saving user's profile
		toUpdate.save();
		
		//success
		flash.success("%s, votre profil a bien été mis à jour", toUpdate.fullname);
		
		render("@AdminController.index", toUpdate);
	}
	
	/** verifie si le formulaire du profil est ok */
	private static void checkIsValidProfil(User user){
		if(validation.hasErrors()){
			params.flash();
			validation.keep();
			render("@AdminController.index",user);
		}
	}
}
------------package controllers;

import models.User;
import notifiers.Mail;

import org.apache.commons.validator.EmailValidator;

import play.data.validation.Required;
import play.mvc.Before;
import play.mvc.Controller;

import common.Utils;

public class SubscriptionController extends Controller {

	@Before
	static void addDefaults() {
		//vide le cache de validation 
		validation.clear();
		flash.clear();
	}

	public static void subscribe(){
		render();
	}
	
	// Subscription of a new user
	public static void register(@Required String email, @Required String password, 
			@Required String confirm ,@Required String username, String website,
			String fullname, String description, boolean rules) throws Throwable {
		
		//le login existe deja
    	User existLogin = User.find("byUsername", username).first();
    	// le mail existe
    	User existMail = User.find("byEmail", email).first();
    	
    	validation.isTrue(existLogin == null).message("Le nom de login est déjà utilisé");
    	checkIsValid();
    	
    	validation.isTrue(existMail == null).message("Le mail est déjà utilisé");
    	checkIsValid();
    	
    	validation.isTrue(EmailValidator.getInstance().isValid(email)).message("L'email fourni n'est pas valide !");
    	checkIsValid();
    	
    	validation.equals(password, confirm).message("Les mots de passe sont différent");
    	checkIsValid();
    	
    	validation.isTrue(Utils.isFordidden(username) == false).message("Le login est interdit");
    	checkIsValid();
    	
    	validation.isTrue(rules).message("Veuillez acceptez les conditions générales d'utilisation du site");
    	checkIsValid();
    	
    	validation.isTrue(description.length() <= 300).message("La description ne doit pas dépasser 300 caractères");
    	checkIsValid();
		
		// Creating the new user
		User newUser = new User(email.trim(), password, username.trim(), fullname, description, website.trim()).save();
		
		// Sending confirmation email
		Mail.confirm(newUser);
		
		flash.success("Vous devez confirmer votre compte via le lien dans l'email que vous allez recevoir.");
		
		//render("Secure/login.html");
		Secure.login();
	}
	
	/** verifie si le formulaire est valide */
	private static void checkIsValid(){
		if (validation.hasErrors()) {
			params.flash();
			validation.keep();
			render("Secure/login.html");
		}
	}

	// User's key validation (account validation)
	public static void confirmUser(String usr, String cKey) {		
		
		User user = User.findById(Long.parseLong(usr));
		
    	validation.isTrue(user != null).message("Erreur! Le login n'existe pas");
    	
    	if(user != null)
    		validation.equals(cKey, user.subscriptionKey);
    	
		if (validation.hasErrors()) {
			params.flash();
			validation.keep();
			render("Secure/login.html");
		}
		
		if (user != null && user.subscriptionKey.equals(cKey)) {
			user.isConfirmed = true;
			user.subscriptionKey = "";
			user.save();
			flash.success("Votre compte est maintenant activé, vous pouvez vous identifier");
		} 
		
		render("Secure/login.html");
	}
	
	/**
	 * envoie d un nouveau password
	 */
	public static void sendNewPassword(String lost) throws Throwable{
		User user = User.find("byEmail",lost.trim()).first();
		validation.isTrue(user != null).message("Cette email n'existe pas");
		if(validation.hasErrors()){
			params.flash();
			render("Secure/login.html");
		}
		String password = user.generateNewPassword();
		user.save();
		//envoie du nouveau mot de passe
		Mail.lostPassword(user,password);
		flash.success("Un nouveau mot de passe vous a été envoyé sur votre adresse email.");
		Secure.login();
	}

	// Checking username's availability
	public static void checkUserAvailability(String username) {
		boolean userAvail = false;
		if (User.find("byUsername", username).first() != null)			
			renderJSON(userAvail);
		renderJSON(userAvail = true);
	}
	
	public static void checkEmailAvailability(String email) {
		boolean emailAvail = false;	

		if (User.find("byEmail", email).first() != null)			
			renderJSON(emailAvail);
		renderJSON(emailAvail = true);
	}
}-------------
package models;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import org.joda.time.DateTime;

import play.data.validation.Required;
import play.db.jpa.Model;

import common.Utils;

/**
 * 
 * Object représente un poste d'une musique par un utilisateur
 * 
 * @author guillaumeleone
 *
 */
@Entity
public class Track extends Model {
	
	//fields
	@Required 
	public String title;
	
	@Required
	public String link;
	
	@Required 
	@Lob
	public String description;
	
	public Date postedAt;
	
	public boolean isValidated;
	
	public int upvote;
	
	public int downvote;
	
	@Required
	@ManyToOne
	public Genre genre;
	
	@Required
	@ManyToOne
	public User artist;
	
	//constructor
	public Track(String title, Genre genre, String link, User artist, String description) {
		this.title = title;
		this.description = description;
		this.genre = genre;
		this.link = link;
		this.artist = artist;
		this.postedAt = new Date();
		this.isValidated = false;
		this.upvote = 0;
		this.downvote = 0;
	}
	
	//method
	/**
	 * retourne la difference entre le + et - de vote
	 */
	public int diff(){
		int result = this.upvote - this.downvote;
		return result < 0 ? 0 : result; 
	}
	
	/**
	 * retourne une liste de track du plus récent au plus ancien
	 */
	public static List<Track> findByDateOrderByDesc(){
		return Track.find("select distinct t, t.postedAt from Track t order by t.postedAt desc").fetch();
	}
	
	public static List<Track> findTrackOfTheDay(){
		return Track.find("select distinct t from Track t where t.postedAt > ? and t.isValidated = true", Utils.toDate()).fetch();
	}
	

	/** 
	 * 	Retourne le top general
	 * 	@param si on souhaite limiter le nombre de résultat
	 * 		   si c'est null on ramene tous les resultats
	 */
	public static List<Track> findTop(Integer number){
		
		JPAQuery query = Track.find("select distinct t, (t.upvote - t.downvote) as result from Track t where t.isValidated = true order by result desc");
		
		if(number != null){
			query.fetch(number);
		}
		return query.fetch();
	}
	
	/** 
	 * 	Retourne le top par mois
	 * 	@param si on souhaite limiter le nombre de résultat
	 */
	public static List<Track> findTopMonth(Integer number){
		
		JPAQuery query = Track.find("select distinct t, (t.upvote - t.downvote) as result from Track t where year(t.postedAt) = ? " + "and month(t.postedAt) = ? and t.isValidated = true order by result desc", 
				new DateTime().getYear(), new DateTime().getMonthOfYear());
		 
		if(number != null){
			query.fetch(number);
		}
		return query.fetch();
	}
	
	public String toString() {
		return title;
	}
}