package controllers;

import actors.SupervisorActor;
import actors.TimeActor;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import models.User;

import play.cache.AsyncCacheApi;
import play.libs.concurrent.HttpExecutionContext;
import play.libs.streams.ActorFlow;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.libs.Json;
import play.libs.ws.WSBodyReadables;
import play.libs.ws.WSBodyWritables;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import play.mvc.WebSocket;

import models.ApiResults;
import models.DataFormatter;
import models.RepositoryCollatedData;
import models.TopicResult;
import models.IssuesWordLevelStatistics;
import models.RepoResults;
import models.RepoResultsDisplay;
import services.TopicService;
import services.UserProfile;
import services.WordLevelIssueStats;
import services.SearchService;
import actors.RepoProfileActor;
import actors.RepoProfileActor.RepoProfileInfo;
import services.RepoProfileService;
import akka.actor.ActorRef;
import scala.compat.java8.FutureConverters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static akka.pattern.Patterns.ask;
import actors.WordLevelIssuesStatsActor;
import actors.WordLevelIssuesStatsActor.IssueStatsInfo;
import static akka.pattern.Patterns.ask;

import services.RepoCommitStatsService;


/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 * @author AKG06
 */
public class HomeController extends Controller implements WSBodyReadables, WSBodyWritables {
    
    @Inject WSClient ws = null;
	ObjectMapper mapper = new ObjectMapper();
	JsonNode jsonObject = mapper.createObjectNode();
    DataFormatter formatter = new DataFormatter();
	String term;
	public ArrayList<String> issueStatisticsTitleList ;	
    ArrayList<String> keyList;  
    // HashMap<String, ArrayList<ApiResults>> resultsList = new HashMap<String, ArrayList<ApiResults>>();
    HashMap<String, RepoResultsDisplay> forKeys = new HashMap<String, RepoResultsDisplay>();
	LinkedHashMap<String, ArrayList<ApiResults>> resultsList = new LinkedHashMap<String, ArrayList<ApiResults>>();
    List<String> searchKeyList = new ArrayList<>();
    //private Cache cache;
	private final AssetsFinder assetsFinder;
	private AsyncCacheApi cache;
	HashMap<String,Object> data;

	private HttpExecutionContext httpExecutionContext;
	
	
	@Inject
	private Materializer materializer;
	@Inject
	private ActorSystem actorSystem;
	ActorRef repoProfileActor;
	
	@Inject
	WordLevelIssueStats wordLevelIssueStats= new WordLevelIssueStats();
	ActorRef statsActor;




	@Inject
	UserProfile usrPl = new UserProfile();

	@Inject 
	TopicService topicService = new TopicService();

	@Inject
	SearchService sr = new SearchService();

	@Inject
	RepoProfileService repoService = new RepoProfileService(ws);

	@Inject 
	RepoCommitStatsService repoCommitStats = new RepoCommitStatsService();

	@Inject
	public HomeController(HttpExecutionContext httpExecutionContext, AssetsFinder assetsFinder, SearchService githubService,  AsyncCacheApi cache, Materializer materializer, ActorSystem actorSystem) {

		this.sr = sr;

		this.cache=cache;
		this.actorSystem=actorSystem;
		this.materializer=materializer;
		this.assetsFinder=assetsFinder;
		this.httpExecutionContext=httpExecutionContext;
		actorSystem.actorOf(TimeActor.props(), "timeActor");
		//repoProfileActor = actorSystem.actorOf(RepoProfileActor.getProps());
		statsActor = actorSystem.actorOf(WordLevelIssuesStatsActor.getProps());
	}

    /**
     * An action that renders an HTML page with a search tab to search and display 10 latest GitHub repositories.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */

	public Result index(Http.Request request) throws InterruptedException, ExecutionException {
		boolean isSearchTermPresent = request.queryString("search").isPresent();
		System.out.println(request.queryString("search"));
		if (request.queryString("search").isPresent()) {
			term =request.queryString("search").get();
			resultsList = sr.getSearchResults(request.queryString("search").get()).toCompletableFuture().get();
			searchKeyList.clear();
			searchKeyList.addAll(resultsList.keySet());
			
			return ok(views.html.index.render(request));
			
		} else {
			return ok(views.html.index.render(request));
		}

	}

	public WebSocket ws() {
		return WebSocket.Json.accept(request -> ActorFlow.actorRef(out -> SupervisorActor.props(out, sr,usrPl,topicService, repoCommitStats, repoService, cache), actorSystem, materializer));
	}
	
	/**
	 * Makes API call with the query provided and returns the data
	 * @author AKG06
	 * @param query
	 * @return Json Data in a HashMap<> to display search results
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */

//	public LinkedHashMap<String, ArrayList<ApiResults>> getResultsFromGitHubApi(String query) throws InterruptedException, ExecutionException {
//		// Added sort=updated in the url to get the lastest repositories on performing search
//		// WSRequest req = ws.url("https://api.github.com/search/repositories?sort=updated&per_page=10&q=" + query);
//		// Kept it here to check data and computations as best match results have good amount of data to test
//		WSRequest cacheRequest = ws.url("https://api.github.com/search/repositories?sort=updated&per_page=10&q=" + query);
//		cacheRequest.setMethod("GET");
//		CompletionStage<JsonNode> res = cacheRequest.get().thenApply(r -> r.asJson());
//		JsonNode nodeObject = Json.toJson(res.toCompletableFuture().get().findPath("items"));
//		return formatter.retrieveArrayOfData(query, nodeObject);
//	}

//	public CompletionStage<Result> getResultsFromGitHubApi(String query,Http.Request request) throws ExecutionException, InterruptedException {
//		boolean isSearchTermPresent = request.queryString("search").isPresent();
//		if (request.queryString("search").isPresent()) {
//			term = request.queryString("search").get();
//			resultsList = sr.getSearchResults(request.queryString("search").get()).toCompletableFuture().get();
//			searchKeyList.clear();
//			searchKeyList.addAll(resultsList.keySet());
//		}
//		CompletionStage<Result> results = sr.getSearchResults(query).thenApplyAsync(repos->ok(views.html.index.render(repos,searchKeyList,isSearchTermPresent)));
//		return results;
//	}

	/**
	 * Makes API call with the query provided, repository data along with entityOption
	 * @author Siddhartha Nanda
	 * @param query	searched query
	 * @param repoCollatedData Data	repository entire data to display
	 * @param entityOption	Call API based on issue or collaborators
	 * @return a entire collated data
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	
	public boolean getIssuesAndCollaboratorsDataFromGitHubApi(String query, RepositoryCollatedData repoCollatedData, String entityOption) throws InterruptedException, ExecutionException {
			WSRequest req = ws.url(query);
			req.setMethod("GET");
			CompletionStage<JsonNode> res = req.get().thenApply(r -> r.asJson());
		JsonNode nodeObject = Json.toJson(res.toCompletableFuture().get());
		return repoCollatedData.getResponseBasedOnQueryParam(nodeObject, entityOption);
	}

	/**
	 * Display respective repository details page with the details like id, name, collaborators, issues, commits
	 * This can be viewed by clicking on any repository name on user profile or main web page.
	 * @author Siddhartha Nanda
	 * @param repoName	user repository name
	 * @param repoId	user repository id
	 * @return a new web page of repository showing repository details
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
// 	public Result getIssuesAndCollaboratorsFromRepo1(String name,String repoName, String repoId) throws InterruptedException, ExecutionException {
// 		for(JsonNode result:formatter.searchMasterData.get(term)) {
// 			if(result.get("id").toString().equals(repoId)) {
// 				jsonObject = result;
// 			break;
// 		}}
// 		RepositoryCollatedData repoCollatedData = new RepositoryCollatedData(jsonObject,repoName, repoId);        	
// 		getIssuesAndCollaboratorsDataFromGitHubApi(repoCollatedData.issue_Url, repoCollatedData, "issues");

// 	    getIssuesAndCollaboratorsDataFromGitHubApi(repoCollatedData.collaborators_url, repoCollatedData, "collaborators");
	    
// 	    issueStatisticsTitleList = new ArrayList<>();
// 		issueStatisticsTitleList = repoCollatedData.issueTitleList;
// //		forKeys = repoCommits(name, repoName);
// 		List<String> keys = new ArrayList<String>(forKeys.keySet());
// 		return ok(views.html.repo.render(repoCollatedData,repoCommits(name, repoName)));
//     }

	public CompletionStage<Result> getIssuesAndCollaboratorsFromRepo (String repoName, String repoId) throws InterruptedException, ExecutionException {
	return FutureConverters
		.toJava(ask(repoProfileActor, new RepoProfileInfo(repoName, "", repoId, repoService), 10000))
		.thenApply(response -> {
			RepositoryCollatedData repoCollatedData = (RepositoryCollatedData) response;
			issueStatisticsTitleList = new ArrayList<>();
			issueStatisticsTitleList = repoCollatedData.issueTitleList;
			return ok(views.html.repo.render(repoCollatedData));
		});
}

	/**
	 * Displays the respective user profile with his repository list and details in a new web page
	 * by clicking on any username from the search results on main web page.
	 * @author Saswati Chowdhury
	 * @param name Name of User
	 * @return a new web page of the provided user Profile showing their name, id and repository list
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public CompletionStage<Result> userRepoList(String name) throws ExecutionException, InterruptedException, IOException {
		CompletionStage<Result> results = usrPl.getUserProfile(name)
				.thenApplyAsync(user -> ok(views.html.userResult.render(user,name)));
		return results;

//		User ur = usrPl.getUserProfile(name);
//		return ok(views.html.userResult.render(ur,name));
	}

	
	
	/**
	 * Display the word-level statistics of the issue titles using Actor -> WordLevelIssuesStatsActor,
	 * counting all unique words in descending order (by frequency of the words) by clicking on the Issue Statistics Button
	 * @author Rajat Kumar
	 * @param request
	 * @return a new web page showing word level statistics from Repository Issues Titles
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	
	
		
	public CompletionStage<Result> issuesStats() throws InterruptedException, ExecutionException  {
		
		keyList=new ArrayList<>();
		
		if(!(issueStatisticsTitleList==null || issueStatisticsTitleList.isEmpty()))
		{
			
			 return FutureConverters
				 		.toJava(ask(statsActor, new IssueStatsInfo(issueStatisticsTitleList), 10000))
				 		.thenApply(response -> {
				 			
				 			data = (HashMap<String,Object>) response;
				 			IssuesWordLevelStatistics s= (IssuesWordLevelStatistics)data.get("list");
				 			
				 			

				 			Iterator iterator = s.frequencyOfWords.keySet().iterator();
				 			while (iterator.hasNext()) {
				 				Object key = iterator.next();
				 				keyList.add((String) key);
				 			}
				 			
				 			return ok(views.html.StatsPage.render(s.frequencyOfWords, keyList));
				 			
				 		});}
		
		else {
			
			return CompletableFuture.supplyAsync(() -> {
		 		return ok(views.html.NoIssues.render());
		});}
		
		
	}
		
		
		/**
		 * Displays the statistics of top-10 committers of the repository and detailed statistics of their commits. 
		 * @author Hasandeep Singh
		 * @param user
		 * @param repo
		 * @return a list of the users and their commits statistics
		 * @throws ExecutionException
		 * @throws InterruptedException
		 */
		public List<RepoResultsDisplay> repoCommits(String user, String repo) throws InterruptedException, ExecutionException 
		{
			WSRequest req = ws.url("https://api.github.com/repos/"+user+"/"+repo+"/commits?per_page=100");
			req.addHeader("Authorization", "Token "+"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");
			req.setMethod("GET");
			CompletionStage<JsonNode> res = req.get().thenApply(r -> r.asJson());
			JsonNode nodeObject = Json.toJson(res.toCompletableFuture().get());
			return retrieveStatData(user,repo, nodeObject);
		}

		/**
		 * Fetches the api page for the commit statistics
		 * @author Hasandeep Singh
		 * @param user
		 * @param repo
		 * @param sha
		 * @return a list of statistics for a commit sha i.e. list of every single addition and deletion of a committer
		 * @throws ExecutionException
		 * @throws InterruptedException
		 */
		public List<RepoResults> fetchStats(String user, String repo, String sha) throws InterruptedException, ExecutionException 
		{
			sha = sha.replaceAll("^\"|\"$", "");
			String url = "https://api.github.com/repos/"+user+"/"+repo+"/commits/"+sha;
			WSRequest req = ws.url(url);
			req.addHeader("Authorization", "Token "+"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");
			req.addHeader("Accept", "application/vnd.github.v3+json");
			req.setMethod("GET");
			CompletionStage<JsonNode> res = req.get().thenApply(r -> r.asJson());
			JsonNode nodeObject = Json.toJson(res.toCompletableFuture().get());
			return formatter.retrieveStats(user,repo, nodeObject);
		}
		
		/**
		 * Fetches actual data for commit statistics and processes min, max and average via streams
		 * @author Hasandeep Singh
		 * @param user
		 * @param repos
		 * @param nodeObject
		 * @return a list of the users and their commits statistics
		 * @throws ExecutionException
		 * @throws InterruptedException
		 */
		public List<RepoResultsDisplay> retrieveStatData(String user,String repos,JsonNode nodeObject) throws InterruptedException, ExecutionException 
		{		
			List<RepoResults> entries = new ArrayList<RepoResults>();
			List<RepoResults> rs_med = new ArrayList<RepoResults>();
			List<RepoResultsDisplay> rs = new ArrayList<RepoResultsDisplay>();
			
			Set<String> userSet = new HashSet<>();
			int min,max,avg,delMin,delMax,delAvg;

			for(JsonNode data:nodeObject) 
			{
				String sha = data.get("sha").toString();
				rs_med = fetchStats(user,repos,sha);

				for (RepoResults rrm : rs_med) 
				{
					userSet.add(rrm.getUser());
					entries.add(rrm);
				}
			}
			
			for (String userStr : userSet) {
				final String k = userStr;
				List<RepoResults> rs_intr_med = new ArrayList<RepoResults>();
				rs_intr_med = entries.stream()
								.filter(usr -> usr.getUser().equals(k))
								.collect(Collectors.toList());
				
				min = rs_intr_med.stream()
				.map(RepoResults::getAddt)
				.mapToInt(value -> value).min().orElse(0);		
				
				max = rs_intr_med.stream()
				.map(RepoResults::getAddt)
				.mapToInt(value -> value).max().orElse(0);		
				
				delMin = rs_intr_med.stream()
				.map(RepoResults::getDel)
				.mapToInt(value -> value).min().orElse(0);		
				
				delMax = rs_intr_med.stream()
				.map(RepoResults::getDel)
				.mapToInt(value -> value).max().orElse(0);

				avg = (int) rs_intr_med.stream()
				.map(RepoResults::getAddt)
				.mapToInt(value -> value).average().orElse(0);
				
				delAvg = (int) rs_intr_med.stream()
				.map(RepoResults::getDel)
				.mapToInt(value -> value).average().orElse(0);
				
				RepoResultsDisplay rslt = new RepoResultsDisplay(userStr,min,max,avg,delMin,delMax,delAvg, 100);
				rs.add(new RepoResultsDisplay(userStr,min,max,avg,delMin,delMax,delAvg, 100));
				System.out.println("Committer "+rslt.getUser()+"addition-min "+rslt.getMin()+"addition-man"+rslt.getMax()+" addition-avg"+rslt.getAvg()
				+" deletion-min "+rslt.getDelMin()+"deletion-max "+rslt.getDelMax()+"deletion-avg"+rslt.getDelAvg());
			}
			
//			HashMap<String,RepoResultsDisplay> rs_final = new HashMap<String,RepoResultsDisplay>();
//			for (RepoResultsDisplay rd : rs) rs_final.put(rd.getKey(),rd);
			return rs;
		}

	/**
	 * Displays list of repositories based on selected topics.
	 * by clicking on any topic from the searched results on main web page.
	 * @author Urvish Tanti
	 * @param topic name of topic
	 * @return a new web page of the provided repositories showing user's name, repository name and topics
	 * @throws ExecutionException
	 * @throws InterruptedException
	 * @throws IOException
	 */

//	public Result getTopics( String topic) throws ExecutionException, InterruptedException, IOException{
//		List<TopicResult> list = tps.getRepoByTopics(topic);
//        return ok(views.html.topic.render(list,topic));
//			}
//

	public CompletionStage<Result> getTopics( String topic) throws ExecutionException, InterruptedException, IOException{
		//List<TopicResult> list = tps.getRepoByTopics(topic);
		CompletionStage<Result> results= topicService.getRepoByTopics(topic)
				.thenApplyAsync(list->ok(views.html.topic.render(list,topic)));
		System.out.println("results"+ results);
		return results;
	}

}
