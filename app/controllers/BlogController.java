package controllers;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import models.Tag;
import models.blog.Post;
import models.blog.PostRef;
import models.user.GAppUser;
import models.user.User;
import play.Logger;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import play.vfs.VirtualFile;
import controllers.secure.Secure;
import annotations.Check;

/**
 * @author clementnivolle
 * @author keruspe
 * @author judu
 */
@With(Secure.class)
@Check(BlogController.CAN_EDIT)
public class BlogController extends Controller {

   public static final String CAN_EDIT = "can_edit_blog";


   /**
    * Print the list of blog posts and permit to edit or delete them
    *
    * @param pagenumber The number of the page to display (20 Post by page)
    */
   public static void listPosts(Integer pagenumber) {
      if (pagenumber == null) {
         pagenumber = 0;
      }
      List<PostRef> postRefs = PostRef.getPostRefsWithPagination(pagenumber, 20);
      List<Post> posts = new ArrayList<Post>();

      for (PostRef pr : postRefs) {
         posts.add(Post.getFirstPostByPostRef(pr));
      }

      render(posts, pagenumber);
   }

   /**
    * Ask confirmation for deleting a Post
    *
    * @param urlId    The urlId of the Post
    * @param language The lang of the Post
    */
   public static void deletePost_confirm(String urlId, String language) {
      Post post = Post.getPostByLocale(urlId, new Locale(language));
      render(post);
   }

   /**
    * Delete a Post
    *
    * @param urlId    The urlId of the Post
    * @param language The lang of the Post
    */
   public static void deletePost(String urlId, String language) {
      Post post = Post.getPostByLocale(urlId, new Locale(language));
      if (post == null) {
         BlogViewer.index();
      }
      PostRef postRef = post.reference;
      post.delete();
      if (Post.getFirstPostByPostRef(postRef) == null) {
         postRef.delete();
      }
      BlogViewer.index();
   }

   /**
    * Create a new Post
    */
   public static void newPost() {
      renderArgs.put("action", "create");
      render();
   }

   /**
    * Edit a Post
    *
    * @param urlId    The urlId of the Post
    * @param language The lang of the Post
    */
   public static void edit(String urlId, String language) {
      Post otherPost = Post.getPostByLocale(urlId, new Locale(language));
      renderArgs.put("otherPost", otherPost);
      renderArgs.put("action", "edit");
      String overrider = null;
      for (Post p : Post.getPostsByPostRef(otherPost.reference)) {
         overrider = "/view/PostEvent/edit/" + p.urlId + ".html";
         if (VirtualFile.fromRelativePath("app/views" + overrider).getRealFile().exists()) {
            break;
         }
      }
      if (!VirtualFile.fromRelativePath("app/views" + overrider).getRealFile().exists()) {
         overrider = "BlogController/newPost.html";
      }
      renderTemplate(overrider);
   }

   /**
    * Translate a Post
    *
    * @param otherUrlId The urlId of the Post to translate
    * @param language   The lang of the Post
    */
   public static void translate(String otherUrlId, String language) {
      Post otherPost = Post.getPostByLocale(otherUrlId, new Locale(language));
      renderArgs.put("otherPost", otherPost);
      renderArgs.put("action", "translate");
      String overrider = null;
      for (Post p : Post.getPostsByPostRef(otherPost.reference)) {
         overrider = "/view/PostEvent/translate/" + p.urlId + ".html";
         if (VirtualFile.fromRelativePath("app/views" + overrider).getRealFile().exists()) {
            break;
         }
      }
      if (!VirtualFile.fromRelativePath("app/views" + overrider).getRealFile().exists()) {
         overrider = "BlogController/newPost.html";
      }
      renderTemplate(overrider);
   }

   /**
    * Create/Edit/Translate a Post
    *
    * @param actionz       The action we're performing ("edit", "create", "translate")
    * @param otherUrlId    The urlId of the Post we're translating or editing (may be null)
    * @param otherLanguage The lang of the Post we're translating or editing (may be null)
    */
   public static void doNewPost(String actionz, String otherUrlId, String otherLanguage) {
      String urlId = params.get("post.urlId");
      String title = params.get("post.title");
      String content = params.get("post.content");
      Locale language = params.get("post.language", Locale.class);
      Date postedAt = new Date();

      // Get connected user.
      User author = AccessManager.getConnected();
      if (author == null) {
         if (AccessManager.isGAppConnected()) {
            author = new GAppUser();
            ((GAppUser) author).firstName = session.get("firstName");
            ((GAppUser) author).lastName = session.get("lastName");
            ((GAppUser) author).openId = AccessManager.connected();
            author.userName = ((GAppUser) author).firstName + " " + ((GAppUser) author).lastName;
            author.email = session.get("email");
            ((GAppUser) author).language = language;

            validation.valid(author);
            if (Validation.hasErrors()) {
               forbidden("Could not authenticate you");
            }
            author.refresh().save();

         } else {
            Logger.error("Nobody connected or the authentication method is not yet supported. If the latter is true, please read the UserManager documentation");
            forbidden("Could not authenticate you");
         }
      }

      Post post = Post.getPostByUrlId(otherUrlId);
      PostRef postRef = null;
      String tagsString = params.get("postReference.tags");
      if (post != null) {
         postRef = post.reference;

         Set<Tag> tags = new TreeSet<Tag>();
         if (tagsString != null && !tagsString.isEmpty()) {
            for (String tag : Arrays.asList(tagsString.split(","))) {
               tags.add(Tag.findOrCreateByName(tag));
            }
         }
         postRef.tags = tags;
      } else {
         postRef = BlogController.doNewPostRef(tagsString, postedAt, author, actionz, otherUrlId, otherLanguage);
      }

      if (!actionz.equals("edit")) {
         post = new Post();
         post.author = author;
      }
      post.reference = postRef;
      post.content = content;
      post.urlId = urlId;
      post.title = title;
      post.postedAt = postedAt;
      post.language = language;

      validation.valid(post);
      if (Validation.hasErrors()) {
         params.flash(); // add http parameters to the flash scope
         Validation.keep(); // keep the errors for the next request
         if (actionz.equals("edit")) {
            BlogController.edit(urlId, otherLanguage);
         } else if (actionz.equals("translate")) {
            BlogController.translate(urlId, otherLanguage);
         } else {
            BlogController.newPost();
         }
      }
      if (actionz.equals("edit")) {
         post.reference.refresh().save();
         post.refresh().save();
      } else {
         post.reference.save();
         post.save();
      }

      BlogViewer.index();
   }

   /**
    * Create a PostRef for a new Post
    *
    * @param actionz       The action we're performing ("edit", "create", "translate")
    * @param otherUrlId    The urlId of the Post we're translating or editing (may be null)
    * @param otherLanguage The lang of the Post we're translating or editing (may be null)
    * @return The PostRef
    */
   private static PostRef doNewPostRef(String tagsString, Date postedAt, User author, String action, String otherUrlId, String otherLanguage) {
      PostRef postRef = new PostRef();
      Set<Tag> tags = new TreeSet<Tag>();
      Tag t = null;

      if (!tagsString.isEmpty()) {
         for (String tag : Arrays.asList(tagsString.split(","))) {
            t = Tag.findOrCreateByName(tag);
            if (!tags.contains(t)) {
               tags.add(t);
            }
         }
      }

      postRef.tags = tags;
      postRef.author = author;
      postRef.postedAt = postedAt;

      validation.valid(postRef);
      if (Validation.hasErrors()) {
         params.flash(); // add http parameters to the flash scope
         Validation.keep(); // keep the errors for the next request
         if (action.equals("edit")) {
            BlogController.edit(otherUrlId, otherLanguage);
         } else if (action.equals("translate")) {
            BlogController.translate(otherUrlId, otherLanguage);
         } else {
            BlogController.newPost();
         }
      }

      return postRef;
   }
}
