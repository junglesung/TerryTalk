package com.vernonsung.terrytalk;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
//import android.support.v4.app.Fragment;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link PasswordFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link PasswordFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PasswordFragment extends Fragment implements View.OnClickListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    // UI
    private TextView textViewPassword;

    public PasswordFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment PasswordFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static PasswordFragment newInstance(String param1, String param2) {
        PasswordFragment fragment = new PasswordFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_password, container, false);
        Button buttonOne = (Button)view.findViewById(R.id.buttonOne);
        Button buttonTwo = (Button)view.findViewById(R.id.buttonTwo);
        Button buttonThree = (Button)view.findViewById(R.id.buttonThree);
        Button buttonFour = (Button)view.findViewById(R.id.buttonFour);
        Button buttonFive = (Button)view.findViewById(R.id.buttonFive);
        Button buttonSix = (Button)view.findViewById(R.id.buttonSix);
        Button buttonSeven = (Button)view.findViewById(R.id.buttonSeven);
        Button buttonEight = (Button)view.findViewById(R.id.buttonEight);
        Button buttonNine = (Button)view.findViewById(R.id.buttonNine);
        Button buttonZero = (Button)view.findViewById(R.id.buttonZero);
        Button buttonBackspace = (Button)view.findViewById(R.id.buttonBackspace);
        TextView textViewConnect = (TextView)view.findViewById(R.id.textViewConnect);
        textViewPassword = (TextView)view.findViewById(R.id.textViewPassword);
        buttonOne.setOnClickListener(this);
        buttonTwo.setOnClickListener(this);
        buttonThree.setOnClickListener(this);
        buttonFour.setOnClickListener(this);
        buttonFive.setOnClickListener(this);
        buttonSix.setOnClickListener(this);
        buttonSeven.setOnClickListener(this);
        buttonEight.setOnClickListener(this);
        buttonNine.setOnClickListener(this);
        buttonZero.setOnClickListener(this);
        buttonBackspace.setOnClickListener(this);
        textViewConnect.setOnClickListener(this);
        return view;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View v) {
        String s = textViewPassword.getText().toString();
        switch (v.getId()) {
            case R.id.buttonOne:
                s = s + "1";
                textViewPassword.setText(s);
                break;
            case R.id.buttonTwo:
                s = s + "2";
                textViewPassword.setText(s);
                break;
            case R.id.buttonThree:
                s = s + "3";
                textViewPassword.setText(s);
                break;
            case R.id.buttonFour:
                s = s + "4";
                textViewPassword.setText(s);
                break;
            case R.id.buttonFive:
                s = s + "5";
                textViewPassword.setText(s);
                break;
            case R.id.buttonSix:
                s = s + "6";
                textViewPassword.setText(s);
                break;
            case R.id.buttonSeven:
                s = s + "7";
                textViewPassword.setText(s);
                break;
            case R.id.buttonEight:
                s = s + "8";
                textViewPassword.setText(s);
                break;
            case R.id.buttonNine:
                s = s + "9";
                textViewPassword.setText(s);
                break;
            case R.id.buttonZero:
                s = s + "0";
                textViewPassword.setText(s);
                break;
            case R.id.buttonBackspace:
                if (!s.isEmpty()) {
                    s = s.substring(0, s.length() - 1);
                    textViewPassword.setText(s);
                }
                break;
            case R.id.textViewConnect:
                break;
        }
    }

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
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
