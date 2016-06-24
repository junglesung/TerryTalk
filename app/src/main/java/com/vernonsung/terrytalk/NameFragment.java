package com.vernonsung.terrytalk;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnChangeWifiP2pNameListener} interface
 * to handle interaction events.
 * Use the {@link NameFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NameFragment extends Fragment implements View.OnClickListener {
    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnChangeWifiP2pNameListener {
        void onChangeWifiP2pName(String name);
    }

    // the fragment initialization parameters
    private static final String ARG_ORIGINAL_NAME = "original_name";
    private String originalName;

    private OnChangeWifiP2pNameListener onChangeWifiP2pNameListener;

    // UI
    private EditText editTextName;
    private Button buttonRename;

    public NameFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param originalName Original Wi-Fi direct device name.
     * @return A new instance of fragment NameFragment.
     */
    public static NameFragment newInstance(String originalName) {
        NameFragment fragment = new NameFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORIGINAL_NAME, originalName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            originalName = getArguments().getString(ARG_ORIGINAL_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view =  inflater.inflate(R.layout.fragment_name, container, false);
        // Get UI elements
        editTextName = (EditText) view.findViewById(R.id.editTextName);
        buttonRename = (Button)view.findViewById(R.id.buttonRename);
        buttonRename.setOnClickListener(this);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (originalName != null && !originalName.isEmpty()) {
            // Show original name
            editTextName.setText(originalName);
            // Let user edit the original name in the tail
            editTextName.requestFocus();
            editTextName.setSelection(originalName.length());
            // Show virtual keyboard
            InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editTextName, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Hide virtual keyboard
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editTextName.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnChangeWifiP2pNameListener) {
            onChangeWifiP2pNameListener = (OnChangeWifiP2pNameListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnChangeWifiP2pNameListener");
        }
    }

    /**
     * Deprecated in API level 23. Keep it here for backward compatibility
     */
    @Deprecated
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnChangeWifiP2pNameListener) {
            onChangeWifiP2pNameListener = (OnChangeWifiP2pNameListener) activity;
        } else {
            throw new RuntimeException(activity.toString()
                    + " must implement OnChangeWifiP2pNameListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        onChangeWifiP2pNameListener = null;
    }

    @Override
    public void onClick(View v) {
        String newName = editTextName.getText().toString();
        if (onChangeWifiP2pNameListener != null && !newName.isEmpty()) {
            onChangeWifiP2pNameListener.onChangeWifiP2pName(newName);
        }
    }
}
