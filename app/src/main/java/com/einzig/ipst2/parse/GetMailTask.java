/******************************************************************************
 *                                                                            *
 * Copyright 2017 Steven Foskett, Jimmy Ho, Ryan Porterfield                  *
 * Permission is hereby granted, free of charge, to any person obtaining a    *
 * copy of this software and associated documentation files (the "Software"), *
 * to deal in the Software without restriction, including without limitation  *
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,   *
 * and/or sell copies of the Software, and to permit persons to whom the      *
 * Software is furnished to do so, subject to the following conditions:       *
 *                                                                            *
 * The above copyright notice and this permission notice shall be included in *
 * all copies or substantial portions of the Software.                        *
 *                                                                            *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,   *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE*
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER     *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING    *
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER        *
 * DEALINGS IN THE SOFTWARE.                                                  *
 *                                                                            *
 ******************************************************************************/

package com.einzig.ipst2.parse;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.einzig.ipst2.R;
import com.einzig.ipst2.oauth.OAuth2Authenticator;
import com.einzig.ipst2.util.DialogHelper;
import com.einzig.ipst2.util.Logger;
import com.einzig.ipst2.util.PreferencesHelper;
import com.sun.mail.imap.IMAPStore;

import org.joda.time.LocalDate;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.NotTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import static com.einzig.ipst2.database.DatabaseInterface.DATE_FORMATTER;

/**
 * @author Ryan Porterfield
 * @since 2017-05-28
 */
public class GetMailTask extends AsyncTask<Void, Void, MailBundle> {
    /** GMail account used for Ingress */
    final private Account account;
    /** Parent activity of this task */
    final private Activity activity;
    /** App preferences */
    final private SharedPreferences preferences;
    /** OAuth token */
    final private String token;

    public GetMailTask(Activity activity, Account account, String token) {
        this.account = account;
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(
                activity);//activity.getPreferences(MainActivity.MODE_PRIVATE);
        this.token = token;
    }

    @Override
    protected MailBundle doInBackground(Void... voids) {
        OAuth2Authenticator sender = new OAuth2Authenticator();
        IMAPStore store = sender.getIMAPStore(account.name, token);
        if (store == null) {
            DialogHelper.showSimpleDialog(R.string.invalidtokentitle_getmailtask,
                    R.string.invalidtokenmessage_getmailtask, activity);
            AccountManager.get(activity).invalidateAuthToken("com.google", token);
        } else {
            try {
                Folder folder = getFolder(store);
                if (folder != null) {
                    folder.open(Folder.READ_ONLY);
                    Message[] messages = searchMailbox(folder);
                    fetchMessages(folder, messages);
                    return new MailBundle(folder, messages, store);
                }
            } catch (MessagingException e) {
                Logger.e(e.toString());
            }
        }
        return null;
    }

    /**
     * Fetch envelope and content info of messages
     *
     * @param folder   Folder messages are contained in
     * @param messages Array of messages that matched the search
     * @see FetchProfile
     */
    private void fetchMessages(Folder folder, Message[] messages) {
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.CONTENT_INFO);
        Logger.d("Fetching messages");
        try {
            folder.fetch(messages, fp);
        } catch (MessagingException e) {
            Logger.e(e.toString());
        }
    }

    private Folder getFolder(IMAPStore store) throws MessagingException {
        Folder[] folders = store.getDefaultFolder().list();
        Folder folder = new FolderGetter(activity, folders, preferences).getFolder();
        if (folder != null) {
            PreferencesHelper helper = new PreferencesHelper(activity.getApplicationContext());
            Logger.d(helper.folderKey() + " -> " + folder.getFullName());
            helper.set(helper.folderKey(), folder.getFullName());
        }
        return folder;
    }

    /**
     * Get the last date email was parsed
     *
     * @param dateStr String representation of the last parse date
     * @return Date the email was parsed if previously parsed, otherwise the date Ingress launched
     */
    private LocalDate getLastParseDate(String dateStr) {
        LocalDate d;
        try {
            d = DATE_FORMATTER.parseLocalDate(dateStr);
        } catch (IllegalArgumentException e) {
            d = new LocalDate(2012, 10, 15);
        }
        return d;
    }

    /**
     * Get SearchTerm to find relevant emails
     *
     * @param lastParseDate Date of previous parse for ReceivedDateTerm
     * @param anySource     boolean that decides if the email search terms 'from' are included.
     * @return Search term that will find all portal submission emails
     */
    private SearchTerm getSearchTerm(LocalDate lastParseDate, boolean anySource) {
        SearchTerm portalTerm = new SubjectTerm("ingress portal");
        SearchTerm reviewTerm = new SubjectTerm("portal review");
        SearchTerm submissionTerm = new SubjectTerm("portal submission");
        SearchTerm submittedTerm = new SubjectTerm("portal submitted");
        SearchTerm subjectTerm = new OrTerm(new SearchTerm[]{portalTerm, reviewTerm,
                submissionTerm, submittedTerm});
        ReceivedDateTerm minDateTerm = new ReceivedDateTerm(ComparisonTerm.GT, lastParseDate.toDate());
        SearchTerm invalidTerm = new NotTerm(new SubjectTerm("invalid"));
        SearchTerm editTerm = new NotTerm(new SubjectTerm("edit"));
        SearchTerm editsTerm = new NotTerm(new SubjectTerm("edits"));
        SearchTerm photoTerm = new NotTerm(new SubjectTerm("photo"));
        SearchTerm superOpsTerm = new FromStringTerm("super-ops@google.com");
        SearchTerm iSupportTerm1 = new FromStringTerm("ingress-support@google.com");
        SearchTerm iSupportTerm2 = new FromStringTerm("ingress-support@nianticlabs.com");
        Logger.d("Last Parse Date: " + lastParseDate.toString());
        SearchTerm fromTerm = new OrTerm(new SearchTerm[]
                {superOpsTerm, iSupportTerm1, iSupportTerm2});
        if (anySource)
            return new AndTerm(new SearchTerm[]
                    {subjectTerm, minDateTerm, invalidTerm, editTerm, editsTerm, photoTerm});
        else
            return new AndTerm(new SearchTerm[]
                    {subjectTerm, minDateTerm, invalidTerm, editTerm, editsTerm, photoTerm,
                            fromTerm});
    }

    /**
     * Search a mail folder for portal submission and response emails
     *
     * @param folder Mail folder containing portal submission emails
     * @return All emails matching the search terms
     * @throws MessagingException if the library encounters an error
     */
    private Message[] searchMailbox(Folder folder) throws MessagingException {
        PreferencesHelper helper = new PreferencesHelper(activity.getApplicationContext());
        LocalDate lastParseDate = getLastParseDate(helper.get(helper.parseDateKey()));
        return folder.search(getSearchTerm(lastParseDate, false));
    }
}
