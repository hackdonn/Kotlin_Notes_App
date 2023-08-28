package com.example.to_doapp.fragments

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.to_doapp.R
import com.example.to_doapp.databinding.FragmentHomeBinding
import com.example.to_doapp.utils.adapter.PinnedTaskAdapter
import com.example.to_doapp.utils.adapter.TaskAdapter
import com.example.to_doapp.utils.model.ToDoData
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlin.math.min

class HomeFragment : Fragment(), ToDoDialogFragment.OnDialogNextBtnClickListener,
    TaskAdapter.TaskAdapterInterface {
    private val TAG = "HomeFragment"
    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: DatabaseReference
    private var frag: ToDoDialogFragment? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var authId: String
    private lateinit var taskAdapter: TaskAdapter
    private var toDoItemList: MutableList<ToDoData> = mutableListOf()
    private var pinnedToDoItemList: MutableList<ToDoData> = mutableListOf()
    val pinnedTaskAdapter = PinnedTaskAdapter(mutableListOf())
    private var isSearchViewEnabled = true
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        getTaskFromFirebase()
        binding.addTaskBtnMain.setOnClickListener {
            if (frag != null)
                childFragmentManager.beginTransaction().remove(frag!!).commit()
            frag = ToDoDialogFragment()
            frag!!.setListener(this)
            frag!!.show(
                childFragmentManager,
                ToDoDialogFragment.TAG
            )
        }
        binding.Edit.setOnClickListener { toggleEditMode() }
        binding.Done.setOnClickListener { toggleEditMode() }
        binding.searchView.setOnTouchListener { _, _ -> !isSearchViewEnabled }
        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!isSearchViewEnabled && hasFocus) {
                binding.searchView.clearFocus()
            }
        }
        binding.DeleteAll.setOnClickListener {
            taskAdapter.deleteSelectedItems()
        }
    }
    private fun toggleEditMode() {
        taskAdapter.toggleSelectionMode()
        isSearchViewEnabled = !isSearchViewEnabled
        binding.searchView.isEnabled = isSearchViewEnabled
        binding.searchView.clearFocus()
        binding.searchView.alpha = if (isSearchViewEnabled) 1.0f else 0.5f
        binding.MoveAll.visibility = if (binding.MoveAll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.DeleteAll.visibility = if (binding.DeleteAll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.noteNum.visibility = if (binding.noteNum.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.addTaskBtnMain.visibility = if (binding.addTaskBtnMain.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.Done.visibility = if (binding.Done.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.Edit.visibility = if (binding.Done.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
    private fun getTaskFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                toDoItemList.clear()
                for (taskSnapshot in snapshot.children) {
                    val taskId = taskSnapshot.key ?: continue
                    val task = taskSnapshot.child("task").getValue(String::class.java) ?: ""
                    val timestamp = taskSnapshot.child("timestamp").getValue(Long::class.java) ?: continue
                    val todoTask = ToDoData(taskId, task, timestamp)
                    toDoItemList.add(todoTask)
                }
                taskAdapter.updateList(toDoItemList)
                taskAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun init() {
        auth = FirebaseAuth.getInstance()
        authId = auth.currentUser!!.uid
        database = Firebase.database.reference.child("Tasks")
            .child(authId)
        binding.mainRecyclerView.setHasFixedSize(true)
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(context)
        toDoItemList = mutableListOf()
        taskAdapter = TaskAdapter(toDoItemList)
        taskAdapter.setListener(this)
        binding.mainRecyclerView.adapter = taskAdapter
        taskAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                updateNoteCount()
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                updateNoteCount()
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                updateNoteCount()
            }
        })
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                filterTasks(newText ?: "")
                return true
            }
        })
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        taskAdapter.removeItem(position)
                    }
                    ItemTouchHelper.RIGHT -> {
                        val item = taskAdapter.removeItem(position)
                        pinnedTaskAdapter.addItem(item)
                    }
                }
            }
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                val icon: Drawable
                val iconMargin: Int
                if (dX > 0) {
                    icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_pin)!!
                    iconMargin = (itemHeight - icon.intrinsicHeight) / 2
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                    icon.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.top + iconMargin + icon.intrinsicHeight)
                } else {
                    icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_baseline_delete_24)!!
                    iconMargin = (itemHeight - icon.intrinsicHeight) / 2
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    icon.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.top + iconMargin + icon.intrinsicHeight)
                }
                icon.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.mainRecyclerView)
        //
        binding.PinnedRecyclerView.setHasFixedSize(true)
        binding.PinnedRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.PinnedRecyclerView.adapter = pinnedTaskAdapter
        val pinnedItemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        taskAdapter.removeItem(position)
                    }
                }
            }
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                val icon: Drawable
                val iconMargin: Int
                if (dX > 0) {
                    icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_pin)!!
                    iconMargin = (itemHeight - icon.intrinsicHeight) / 2
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                    icon.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.top + iconMargin + icon.intrinsicHeight)
                } else {
                    icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_baseline_delete_24)!!
                    iconMargin = (itemHeight - icon.intrinsicHeight) / 2
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    icon.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.top + iconMargin + icon.intrinsicHeight)
                }
                icon.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        val pinnedItemTouchHelper = ItemTouchHelper(pinnedItemTouchHelperCallback)
        pinnedItemTouchHelper.attachToRecyclerView(binding.PinnedRecyclerView)


    }
    private fun filterTasks(query: String) {
        val filteredList = toDoItemList.filter { it.task.contains(query, ignoreCase = true) }
        taskAdapter.updateList(filteredList)
        //
        val filteredPinnedList = pinnedToDoItemList.filter { it.task.contains(query, ignoreCase = true) }
        pinnedTaskAdapter.updateList(filteredPinnedList)
    }
    private fun updateNoteCount() {
        val itemCount = taskAdapter.itemCount + pinnedTaskAdapter.itemCount
        binding.noteNum.text = when (itemCount) {
            0 -> "No Notes"
            1 -> "1 Note"
            else -> "$itemCount Notes"
        }
    }
    override fun saveTask(todoTask: String, todoEdit: TextInputEditText) {
        val taskId = database.push().key ?: return
        val newTask = ToDoData(taskId, todoTask)
        database.child(taskId).setValue(newTask).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(context, "Task Added Successfully", Toast.LENGTH_SHORT).show()
                todoEdit.text = null
            } else {
                Toast.makeText(context, it.exception.toString(), Toast.LENGTH_SHORT).show()
            }
        }
        frag!!.dismiss()
    }
    override fun updateTask(toDoData: ToDoData, todoEdit: TextInputEditText) {
        val map = HashMap<String, Any>()
        map[toDoData.taskId] = toDoData.task
        database.updateChildren(map).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(context, "Updated Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, it.exception.toString(), Toast.LENGTH_SHORT).show()
            }
            frag!!.dismiss()
        }
    }
    override fun onDeleteItemClicked(toDoData: ToDoData, position: Int) {
        Log.d(TAG, "Deleting task with id: ${toDoData.taskId}")
        database.child(toDoData.taskId).removeValue().addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d(TAG, "Task deleted successfully")
            } else {
                Log.e(TAG, "Failed to delete task", it.exception)
            }
        }
    }
    override fun onEditItemClicked(toDoData: ToDoData, position: Int) {
        if (frag != null)
            childFragmentManager.beginTransaction().remove(frag!!).commit()
        frag = ToDoDialogFragment.newInstance(toDoData.taskId, toDoData.task)
        frag!!.setListener(this)
        frag!!.show(
            childFragmentManager,
            ToDoDialogFragment.TAG
        )
    }
}