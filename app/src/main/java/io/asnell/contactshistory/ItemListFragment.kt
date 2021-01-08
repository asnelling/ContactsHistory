package io.asnell.contactshistory

import android.Manifest.permission.READ_CONTACTS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.Contacts
import android.text.format.DateFormat.getDateFormat
import android.text.format.DateFormat.getTimeFormat
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.RecyclerView
import io.asnell.contactshistory.placeholder.PlaceholderContent
import io.asnell.contactshistory.databinding.FragmentItemListBinding
import io.asnell.contactshistory.databinding.ItemListContentBinding
import java.lang.IllegalStateException
import java.text.DateFormat
import java.util.*

private val PROJECTION: Array<out String> = arrayOf(
        Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
)

/**
 * A Fragment representing a list of Pings. This fragment
 * has different presentations for handset and larger screen devices. On
 * handsets, the fragment presents a list of items, which when touched,
 * lead to a {@link ItemDetailFragment} representing
 * item details. On larger screens, the Navigation controller presents the list of items and
 * item details side-by-side using two vertical panes.
 */

class ItemListFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * Method to intercept global key events in the
     * item list fragment to trigger keyboard shortcuts
     * Currently provides a toast when Ctrl + Z and Ctrl + F
     * are triggered
     */
    private val unhandledKeyEventListenerCompat = ViewCompat.OnUnhandledKeyEventListenerCompat { v, event ->
        if (event.keyCode == KeyEvent.KEYCODE_Z && event.isCtrlPressed) {
            Toast.makeText(
                    v.context,
                    "Undo (Ctrl + Z) shortcut triggered",
                    Toast.LENGTH_LONG
            ).show()
            true
        } else if (event.keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed) {
            Toast.makeText(
                    v.context,
                    "Find (Ctrl + F) shortcut triggered",
                    Toast.LENGTH_LONG
            ).show()
            true
        }
        false
    }

    private var _binding: FragmentItemListBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mLoaderManager: LoaderManager

    private val contacts: MutableList<ContactItem> = mutableListOf()

    private var adapter: SimpleItemRecyclerViewAdapter? = null

    private lateinit var dateFormat: DateFormat
    private lateinit var timeFormat: DateFormat

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dateFormat = getDateFormat(context)
        timeFormat = getTimeFormat(context)
    }

    private fun setupLoader() {
        mLoaderManager = LoaderManager.getInstance(this)
        mLoaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return activity?.let {
            return CursorLoader(it, Contacts.CONTENT_URI, PROJECTION, null, null, null)
        } ?: throw IllegalStateException()
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        val idColumnId = data.getColumnIndex(Contacts._ID)
        val contactKeyId = data.getColumnIndex(Contacts.LOOKUP_KEY)
        val displayNameColumnId = data.getColumnIndex(Contacts.DISPLAY_NAME)
        val timestampColumnId = data.getColumnIndex(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
        Log.d(TAG, "finished loading contacts. count: ${data.count}")
        var id = 0
        while (data.moveToNext()) {
            ++id
            val contactId = data.getLong(idColumnId)
            val contactKey = data.getString(contactKeyId)
            val lookupUri = Contacts.getLookupUri(contactId, contactKey)
            val date = Date(data.getLong(timestampColumnId))
            Log.d(TAG, "contact timestamp: $date")
            Log.d(TAG, "contact lookup URI: $lookupUri")
            val formattedDate = dateFormat.format(date)
            val formattedTime = timeFormat.format(date)
            val datetime = "$formattedDate $formattedTime"
            contacts.add(ContactItem(id.toString(), data.getString(displayNameColumnId), datetime, lookupUri))
        }

        adapter?.resetData(contacts)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        Log.d(TAG, "loader reset")
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {

        _binding = FragmentItemListBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.addOnUnhandledKeyEventListener(view, unhandledKeyEventListenerCompat)

        val recyclerView: RecyclerView = binding.itemList

        /** Click Listener to trigger navigation based on if you have
         * a single pane layout or two pane layout
         */
        val onClickListener = View.OnClickListener { itemView ->
            val item = itemView.tag as ContactItem
            val intent = Intent(Intent.ACTION_VIEW, item.lookupUri)
            startActivity(intent)
        }

        setupRecyclerView(recyclerView, onClickListener)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setupLoader()
            } else {
                Log.e(TAG, "read contacts permission denied")
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), READ_CONTACTS) == PERMISSION_GRANTED) {
            setupLoader()
        } else {
            requestPermissionLauncher.launch(READ_CONTACTS)
        }
    }

    private fun setupRecyclerView(
            recyclerView: RecyclerView,
            onClickListener: View.OnClickListener
    ) {

        adapter = SimpleItemRecyclerViewAdapter(contacts, onClickListener)
        recyclerView.adapter = adapter
    }

    class SimpleItemRecyclerViewAdapter(
            private var values: List<ContactItem>,
            private val onClickListener: View.OnClickListener
    ) :
            RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        fun resetData(newValues: List<ContactItem>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val binding = ItemListContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.nameView.text = item.displayName
            holder.datetimeView.text = item.changed

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(binding: ItemListContentBinding) : RecyclerView.ViewHolder(binding.root) {
            val nameView: TextView = binding.name
            val datetimeView: TextView = binding.datetime
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ItemListFragment"
    }
}