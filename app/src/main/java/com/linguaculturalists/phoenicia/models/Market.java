package com.linguaculturalists.phoenicia.models;

import com.linguaculturalists.phoenicia.PhoeniciaGame;
import com.linguaculturalists.phoenicia.locale.Letter;
import com.linguaculturalists.phoenicia.locale.Level;
import com.linguaculturalists.phoenicia.locale.Locale;
import com.linguaculturalists.phoenicia.locale.Person;
import com.linguaculturalists.phoenicia.locale.Word;
import com.linguaculturalists.phoenicia.util.PhoeniciaContext;
import com.orm.androrm.Filter;

import org.andengine.util.debug.Debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Managing class for generating and querying market requests
 */
public class Market {
    private static Market instance;
    private PhoeniciaGame game;
    private GameSession session;
    private List<MarketUpdateListener> listeners;

    private Market(PhoeniciaGame game) {
        this.game = game;
        this.session = game.session;
        this.listeners = new LinkedList<MarketUpdateListener>();
    }

    /**
     * Initialize the singleton for a given session
     * @param game
     */
    public static void init(PhoeniciaGame game) {
        instance = new Market(game);
    }

    /**
     * Returns an Singleton instance of the Inventory class.
     * You must call init(PhoeniciaGame) before calling this method
     * @return
     */
    public static Market getInstance() {
        return instance;
    }

    /**
     * Retrieve a list of all active requests in the market
     * @return a list of active \link MarketRequest MarketRequests \endlink
     */
    public List<MarketRequest> requests() {
        Filter openRequests = new Filter();
        openRequests.is("status", MarketRequest.REQUESTED);
        List<MarketRequest> items = MarketRequest.objects(PhoeniciaContext.context).filter(this.session.filter).filter(openRequests).toList();
        Debug.d("Found "+items.size()+" requests");
        return items;
    }

    public boolean isFull() {
        return (this.neededToFill() <= 0);
    }

    public int neededToFill() {
        int limit = this.game.locale.level_map.get(this.game.current_level).marketRequests;
        Debug.d("Level " + this.game.current_level + " accepts up to " + limit + " requests");
        int needed = limit - this.requests().size();
        Debug.d("Need "+needed+" more requests");
        return needed;
    }
    /**
     * Make sure there are as many requests as the current game level allows, creating more if necessary
     */
    public void populate() {
        Debug.d("Populating marketplace");
        int needed = this.neededToFill();
        if (needed > 0) {
            for (int i = 0; i < needed; i++) {
                MarketRequest newRequest = this.createRequest();
                Debug.d("Adding request from "+newRequest.person_name.get());
            }
        }
    }

    /**
     * Generate a new request based on the current game level
     * @return newly created and saved request
     */
    public MarketRequest createRequest() {
        Date now = new Date();
        MarketRequest request = new MarketRequest();
        request.game.set(this.session);
        // TODO: refactor to be more efficient when adding more than one request at a time
        List<String> existing_persons = new ArrayList<String>();
        for (MarketRequest existing_request : this.requests()) {
            existing_persons.add(existing_request.person_name.get());
        }
        List<String> available_persons = new ArrayList<String>();
        for (Person check_person : this.game.locale.people) {
            if (!existing_persons.contains(check_person.name)) {
                available_persons.add(check_person.name);
            }
        }
        if (available_persons.size() < 1) {
            Debug.e("Not enough people for all requests!");
            return null;
        }
        int person_id = Math.round((float)Math.random() * (available_persons.size()-1));
        request.person_name.set(available_persons.get(person_id));
        request.status.set(MarketRequest.REQUESTED);
        request.requested.set((double) now.getTime());
        request.save(PhoeniciaContext.context);

        double requestType = Math.random() * 10;
        if (requestType < 2) { // 0-2
            Debug.d("Creating current level random request for "+available_persons.get(person_id));
            this.populateCurrentLevelRandom(request);
        } else if (requestType < 7) { // 2-7
            Debug.d("Creating inventory reduction request for "+available_persons.get(person_id));
            this.populateInventoryReduction(request);
        } else if (requestType < 9) { // 7-9
            Debug.d("Creating word practice request for "+available_persons.get(person_id));
            this.populateWordPractice(request);
        } else { // 9-10
            Debug.d("Creating next level pusher request for "+available_persons.get(person_id));
            this.populateNextLevelPusher(request);
        }
        request.save(PhoeniciaContext.context);

        this.requestAdded(request);
        return request;
    }

    private RequestItem createRandomRequestLetter(List<Letter> choiceLetters) {
        return null;
    }

    private RequestItem createRandomRequestWord() {
        return null;
    }

    private void populateInventoryReduction(final MarketRequest request) {
        float multiplier = 1.2f;
        int num_items = Math.round((float) Math.random() * (this.game.locale.level_map.get(this.game.current_level).marketRequests - 1)) + 1;

        Filter positiveQuantity = new Filter();
        positiveQuantity.is("quantity", ">", 0);

        final List<InventoryItem> inventoryItems = InventoryItem.objects(PhoeniciaContext.context).filter(this.game.session.filter).filter(positiveQuantity).orderBy("#-quantity").limit(5).toList();
        int requestCoins = 0;
        int requestPoints = 0;

        List<String> usedList = new ArrayList<String>();
        for (int i = 0; i < num_items; ) {

            int randomItem = Math.round((float) Math.random() * (inventoryItems.size()-1));
            String item_name = inventoryItems.get(randomItem).item_name.get();

            // TODO: pick letters and words based on inventory, history and level
            if (this.game.locale.letter_map.containsKey(item_name)) {
                RequestItem requestLetter = new RequestItem();
                requestLetter.game.set(this.session);
                requestLetter.request.set(request);

                if (usedList.contains(item_name)) continue;
                usedList.add(item_name);

                requestLetter.item_name.set(item_name);
                requestLetter.quantity.set((int)Math.round(Math.random() * 7)+1);
                requestLetter.save(PhoeniciaContext.context);
                requestCoins += (this.game.locale.letter_map.get(item_name).sell * requestLetter.quantity.get());
                requestPoints += (this.game.locale.letter_map.get(item_name).points * requestLetter.quantity.get());
                i += 1;
            } else if (this.game.locale.word_map.containsKey(item_name)) {
                RequestItem requestWord = new RequestItem();
                requestWord.game.set(this.session);
                requestWord.request.set(request);

                if (usedList.contains(item_name)) continue;
                usedList.add(item_name);

                requestWord.item_name.set(item_name);
                requestWord.quantity.set((int)Math.round(Math.random() * 4)+1);
                requestWord.save(PhoeniciaContext.context);
                requestCoins += (this.game.locale.word_map.get(item_name).sell * requestWord.quantity.get());
                requestPoints += (this.game.locale.word_map.get(item_name).points * requestWord.quantity.get());
                i += 1;
            }

        }
        request.coins.set((int)(requestCoins * multiplier));
        request.points.set(requestPoints);
    }

    private void populateCurrentLevelRandom(final MarketRequest request) {
        float multiplier = 1.5f;
        int num_items = Math.round((float) Math.random() * (this.game.locale.level_map.get(this.game.current_level).marketRequests - 1)) + 1;

        final List<Letter> levelLetters = this.game.locale.level_map.get(this.game.current_level).letters;
        final List<Word> levelWords = this.game.locale.level_map.get(this.game.current_level).words;
        int requestCoins = 0;
        int requestPoints = 0;
        List<String> usedList = new ArrayList<String>();
        for (int i = 0; i < num_items; ) {

            // TODO: pick letters and words based on inventory, history and level
            if (levelLetters.size() > 0) {
                RequestItem requestLetter = new RequestItem();
                requestLetter.game.set(this.session);
                requestLetter.request.set(request);
                int randomLetter = Math.round((float) Math.random() * (levelLetters.size() - 1));

                if (usedList.contains(levelLetters.get(randomLetter).name)) continue;
                usedList.add(levelLetters.get(randomLetter).name);

                requestLetter.item_name.set(levelLetters.get(randomLetter).name);
                requestLetter.quantity.set(Math.round((float) Math.random() * 5)+1);
                requestLetter.save(PhoeniciaContext.context);
                requestCoins += (levelLetters.get(randomLetter).sell * requestLetter.quantity.get());
                requestPoints += (levelLetters.get(randomLetter).points * requestLetter.quantity.get());
                i += 1;
            }

            if (levelWords.size() > 0) {
                RequestItem requestWord = new RequestItem();
                requestWord.game.set(this.session);
                requestWord.request.set(request);
                int randomWord = Math.round((float) Math.random() * (levelWords.size() - 1));

                if (usedList.contains(levelWords.get(randomWord).name)) continue;
                usedList.add(levelWords.get(randomWord).name);

                requestWord.item_name.set(levelWords.get(randomWord).name);
                requestWord.quantity.set(Math.round((float) Math.random() * 3)+1);
                requestWord.save(PhoeniciaContext.context);
                requestCoins += (levelWords.get(randomWord).sell * requestWord.quantity.get());
                requestPoints += (levelWords.get(randomWord).points * requestWord.quantity.get());
                i += 1;
            }

        }
        request.coins.set((int)(requestCoins * multiplier));
        request.points.set(requestPoints);
    }

    private void populateNextLevelPusher(final MarketRequest request) {
        float multiplier = 1.9f;
        Level prev_level = this.game.locale.level_map.get(this.game.current_level).prev;
        Level next_level = this.game.locale.level_map.get(this.game.current_level).next;

        List<Letter> next_letters = new ArrayList<Letter>(next_level.letters);
        next_letters.removeAll(prev_level.letters);

        List<Word> next_words = new ArrayList<Word>(next_level.words);
        next_words.removeAll(prev_level.words);

        int num_items = Math.round((float) Math.random() * (this.game.locale.level_map.get(this.game.current_level).marketRequests - 1)) + 1;

        int requestCoins = 0;
        int requestPoints = 0;
        List<String> usedList = new ArrayList<String>();
        for (int i = 0; i < num_items; ) {

            // TODO: pick letters and words based on inventory, history and level
            if (next_letters.size() > 0) {
                RequestItem requestLetter = new RequestItem();
                requestLetter.game.set(this.session);
                requestLetter.request.set(request);
                int randomLetter = Math.round((float) Math.random() * (next_letters.size() - 1));

                requestLetter.item_name.set(next_letters.get(randomLetter).name);
                requestLetter.quantity.set(Math.round((float) Math.random() * 2)+1);
                requestLetter.save(PhoeniciaContext.context);

                next_letters.remove(requestLetter);

                requestCoins += (next_letters.get(randomLetter).sell * requestLetter.quantity.get());
                requestPoints += (next_letters.get(randomLetter).points * requestLetter.quantity.get());
                i += 1;
            }
            if (next_words.size() > 0) {
                RequestItem requestWord = new RequestItem();
                requestWord.game.set(this.session);
                requestWord.request.set(request);
                int randomWord = Math.round((float) Math.random() * (next_words.size() - 1));

                requestWord.item_name.set(next_words.get(randomWord).name);
                requestWord.quantity.set(Math.round((float) Math.random() * 1)+1);
                requestWord.save(PhoeniciaContext.context);

                next_words.remove(requestWord);

                requestCoins += (next_words.get(randomWord).sell * requestWord.quantity.get());
                requestPoints += (next_words.get(randomWord).points * requestWord.quantity.get());
                i += 1;
            }
            if ( next_letters.size() < 1 &&  next_words.size() < 1) break;

        }
        request.coins.set((int)(requestCoins * multiplier));
        request.points.set(requestPoints);
    }

    private void populateWordPractice(final MarketRequest request) {
        float multiplier = 1.7f;
        int num_items = Math.round((float) Math.random() * (this.game.locale.level_map.get(this.game.current_level).marketRequests - 1)) + 1;

        final List<InventoryItem> inventoryItems = InventoryItem.objects(PhoeniciaContext.context).filter(this.game.session.filter).orderBy("#+history").limit(10).toList();
        int requestCoins = 0;
        int requestPoints = 0;
        List<String> usedList = new ArrayList<String>();
        for (int i = 0; i < num_items; ) {

            int randomItem = Math.round((float) Math.random() * (inventoryItems.size()-1));
            String item_name = inventoryItems.get(randomItem).item_name.get();

            // TODO: pick letters and words based on inventory, history and level
            if (this.game.locale.letter_map.containsKey(item_name)) {
                RequestItem requestLetter = new RequestItem();
                requestLetter.game.set(this.session);
                requestLetter.request.set(request);

                if (usedList.contains(item_name)) continue;
                usedList.add(item_name);

                requestLetter.item_name.set(item_name);
                requestLetter.quantity.set(Math.round((float) Math.random() * 5)+1);
                requestLetter.save(PhoeniciaContext.context);
                requestCoins += (this.game.locale.letter_map.get(item_name).sell * requestLetter.quantity.get());
                requestPoints += (this.game.locale.letter_map.get(item_name).points * requestLetter.quantity.get());
                i += 1;
            } else if (this.game.locale.word_map.containsKey(item_name)) {
                RequestItem requestWord = new RequestItem();
                requestWord.game.set(this.session);
                requestWord.request.set(request);

                if (usedList.contains(item_name)) continue;
                usedList.add(item_name);

                requestWord.item_name.set(item_name);
                requestWord.quantity.set(Math.round((float) Math.random() * 3)+1);
                requestWord.save(PhoeniciaContext.context);
                requestCoins += (this.game.locale.word_map.get(item_name).sell * requestWord.quantity.get());
                requestPoints += (this.game.locale.word_map.get(item_name).points * requestWord.quantity.get());
                i += 1;
            }

        }
        request.coins.set((int)(requestCoins * multiplier));
        request.points.set(requestPoints);
    }

    /**
     * Mark a given request as having been fulfilled, and at the same time subtract the requested
     * items from the player's inventory, and credit the player for the coins and experience points
     * offered in the request
     * @param request
     */
    public void fulfillRequest(MarketRequest request) {
        Debug.d("Completing sale to " + request.person_name.get());
        for (RequestItem item : request.getItems(PhoeniciaContext.context)) {
            try {
                Inventory.getInstance().subtract(item.item_name.get(), item.quantity.get());
            } catch (Exception e) {
                Debug.e("Failed to subtract inventory item "+item.item_name.get()+" while completing sale");
                return;
            }
        }
        Bank.getInstance().credit(request.coins.get());
        this.game.session.addExperience(request.points.get());
        request.status.set(MarketRequest.FULFILLED);
        request.save(PhoeniciaContext.context);
        this.requestFulfilled(request);
    }

    public void cancelRequest(MarketRequest request) {
        request.status.set(MarketRequest.CANCELED);
        request.save(PhoeniciaContext.context);
        this.requestCanceled(request);
    }
    /**
     * Delete all Market requests
     */
    public void clear() {
        List<MarketRequest> items = MarketRequest.objects(PhoeniciaContext.context).filter(this.session.filter).toList();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).delete(PhoeniciaContext.context);
        }
    }

    private void requestAdded(MarketRequest request) {
        for (MarketUpdateListener listener : this.listeners) {
            listener.onMarketRequestAdded(request);
        }
    }

    private void requestFulfilled(MarketRequest request) {
        for (MarketUpdateListener listener : this.listeners) {
            listener.onMarketRequestFulfilled(request);
        }
    }

    private void requestCanceled(MarketRequest request) {
        for (MarketUpdateListener listener : this.listeners) {
            listener.onMarketRequestCanceled(request);
        }
    }

    public void addUpdateListener(MarketUpdateListener listener) {
        this.listeners.add(listener);
    }
    public void removeUpdateListener(MarketUpdateListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Callback listener for changes to item quantities in the inventory.
     */
    public interface MarketUpdateListener {
        public void onMarketRequestAdded(final MarketRequest request);
        public void onMarketRequestFulfilled(final MarketRequest request);
        public void onMarketRequestCanceled(final MarketRequest request);
    }

}
